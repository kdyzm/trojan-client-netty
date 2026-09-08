package com.kdyzm.trojan.client.netty.inbound;

import com.kdyzm.trojan.client.netty.encoder.TrojanRequestEncoder;
import com.kdyzm.trojan.client.netty.router.ProxyDecision;
import com.kdyzm.trojan.client.netty.router.ProxyRouter;
import com.kdyzm.trojan.client.netty.properties.ConfigProperties;
import com.kdyzm.trojan.client.netty.util.SslUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socksx.v5.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SOCKS5 CONNECT 命令处理：统一决策（ProxyRouter）后建立出站连接。
 * 收敛后的线程模型（Phase 2）：客户端 pipeline 增删与 relay 激活全部经由
 * 客户端 channel 的 eventLoop 串行执行，杜绝跨线程 pipeline 竞态。
 *
 * @author kdyzm
 * @date 2021-04-23
 */
@Slf4j
@AllArgsConstructor
public class Socks5CommandRequestInboundHandler extends SimpleChannelInboundHandler<DefaultSocks5CommandRequest> {

    /**
     * 出站连接超时时间，与 http 代理路径保持一致
     */
    private static final int CONNECT_TIMEOUT_MILLIS = 2000;

    private EventLoopGroup eventExecutors;

    private ProxyRouter proxyRouter;

    private ConfigProperties configProperties;

    private RelayHandler relayHandler;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5CommandRequest msg) throws Exception {
        Socks5AddressType socks5AddressType = msg.dstAddrType();
        if (!msg.type().equals(Socks5CommandType.CONNECT)) {
            //仅支持 CONNECT；UDP ASSOCIATE / BIND 等命令无法处理，明确拒绝并关闭连接
            log.warn("不支持的 SOCKS5 命令类型: {}", msg.type());
            DefaultSocks5CommandResponse response = new DefaultSocks5CommandResponse(
                    Socks5CommandStatus.COMMAND_UNSUPPORTED, msg.dstAddrType());
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        //统一决策：BLOCK 拒绝、PROXY 走 trojan、DIRECT 直连
        ProxyDecision decision = proxyRouter.decide(msg.dstAddr());
        if (decision == ProxyDecision.BLOCK) {
            log.info("{} 地址在黑名单中，拒绝连接", msg.dstAddr());
            //假装连接成功
            DefaultSocks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, socks5AddressType);
            ctx.writeAndFlush(commandResponse);
            ctx.pipeline().addLast("HttpServerCodec", new HttpServerCodec());
            ctx.pipeline().addLast(new BlackListInboundHandler());
            ctx.pipeline().remove(Socks5CommandRequestInboundHandler.class);
            ctx.pipeline().remove(Socks5CommandRequestDecoder.class);
            //静态链的 relay 需随握手 handler 一并移除，否则黑名单拦截页的数据会被未激活的 relay 释放（Task 3 审查回归发现）
            ctx.pipeline().remove(RelayHandler.class);
            return;
        }
        log.debug("准备连接目标服务器，ip={},port={}", msg.dstAddr(), msg.dstPort());
        Bootstrap bootstrap = new Bootstrap();
        bootstrap = bootstrap.group(eventExecutors)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS);
        if (decision == ProxyDecision.PROXY) {
            proxyConnect(ctx, msg, socks5AddressType, bootstrap);
        } else {
            directConnect(ctx, msg, socks5AddressType, bootstrap);
        }
    }

    /**
     * 连接成功后的统一处理：必须在客户端 eventLoop 上执行。
     * 移除握手 decoder 与自身、激活 relay、回 SUCCESS——全程单线程，无 pipeline 竞态
     */
    private void onConnectSuccess(ChannelHandlerContext ctx, Channel outboundChannel,
                                  Socks5AddressType socks5AddressType) {
        if (!ctx.channel().isActive()) {
            //客户端已在出站连接建立期间断开：relay 未激活，其 channelInactive 已过（无级联），直接关闭出站连接防止孤儿化
            log.info("客户端连接已断开，关闭出站连接");
            outboundChannel.close();
            return;
        }
        ctx.pipeline().remove(Socks5CommandRequestDecoder.class);
        ctx.pipeline().remove(Socks5CommandRequestInboundHandler.class);
        relayHandler.activate(outboundChannel);
        DefaultSocks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, socks5AddressType);
        ctx.writeAndFlush(commandResponse);
    }

    private void onConnectFailure(ChannelHandlerContext ctx, Socks5AddressType socks5AddressType,
                                  String target, int port) {
        log.error("连接目标服务器失败,address={},port={}", target, port);
        DefaultSocks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, socks5AddressType);
        ctx.writeAndFlush(commandResponse);
    }

    private void directConnect(ChannelHandlerContext ctx, DefaultSocks5CommandRequest msg,
                               Socks5AddressType socks5AddressType, Bootstrap bootstrap) {
        log.info("[direct][socks5] {}:{}", msg.dstAddr(), msg.dstPort());
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                //出站侧固定装配：数据回写客户端，断开级联关闭——全部在出站 channel 的 eventLoop 上完成
                ch.pipeline().addLast(new RelayHandler(ctx.channel()));
            }
        });
        ChannelFuture future = bootstrap.connect(msg.dstAddr(), msg.dstPort());
        future.addListener((ChannelFuture f) -> {
            Channel clientChannel = ctx.channel();
            if (f.isSuccess()) {
                //所有对客户端 pipeline 的操作调度到客户端 eventLoop，与原 IO 事件串行
                clientChannel.eventLoop().execute(() -> onConnectSuccess(ctx, f.channel(), socks5AddressType));
            } else {
                clientChannel.eventLoop().execute(() -> onConnectFailure(ctx, socks5AddressType, msg.dstAddr(), msg.dstPort()));
            }
        });
    }

    private void proxyConnect(ChannelHandlerContext ctx, DefaultSocks5CommandRequest msg,
                              Socks5AddressType socks5AddressType, Bootstrap bootstrap) {
        log.info("[proxy][socks5] {}:{}", msg.dstAddr(), msg.dstPort());
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(SslUtil.getContext().newHandler(ch.alloc()));
                ch.pipeline().addLast(new TrojanRequestEncoder(
                        configProperties.getTrojanPassword(),
                        socks5AddressType.byteValue(),
                        msg.dstAddr(),
                        msg.dstPort()));
                //出站侧固定装配：服务端数据回写客户端
                ch.pipeline().addLast(new RelayHandler(ctx.channel()));
            }
        });
        ChannelFuture future = bootstrap.connect(configProperties.getTrojanServerHost(), configProperties.getTrojanServerPort());
        future.addListener((ChannelFuture f) -> {
            Channel clientChannel = ctx.channel();
            if (f.isSuccess()) {
                clientChannel.eventLoop().execute(() -> onConnectSuccess(ctx, f.channel(), socks5AddressType));
            } else {
                clientChannel.eventLoop().execute(() -> onConnectFailure(ctx, socks5AddressType,
                        configProperties.getTrojanServerHost(), configProperties.getTrojanServerPort()));
            }
        });
    }
}

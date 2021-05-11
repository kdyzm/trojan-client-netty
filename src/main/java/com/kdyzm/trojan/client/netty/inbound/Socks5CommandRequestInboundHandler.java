package com.kdyzm.trojan.client.netty.inbound;

import com.kdyzm.trojan.client.netty.encoder.TrojanRequestEncoder;
import com.kdyzm.trojan.client.netty.enums.PacModeEnum;
import com.kdyzm.trojan.client.netty.enums.ProxyModelEnum;
import com.kdyzm.trojan.client.netty.models.PacModel;
import com.kdyzm.trojan.client.netty.properties.ConfigProperties;
import com.kdyzm.trojan.client.netty.util.SslUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.util.ReferenceCountUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;

/**
 * @author kdyzm
 * @date 2021-04-23
 */
@Slf4j
@AllArgsConstructor
public class Socks5CommandRequestInboundHandler extends SimpleChannelInboundHandler<DefaultSocks5CommandRequest> {

    private EventLoopGroup eventExecutors;

    private Map<String, PacModel> blackList;

    private ConfigProperties configProperties;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5CommandRequest msg) throws Exception {
        Socks5AddressType socks5AddressType = msg.dstAddrType();
        if (!msg.type().equals(Socks5CommandType.CONNECT)) {
            log.debug("receive commandRequest type={}", msg.type());
            ReferenceCountUtil.retain(msg);
            ctx.fireChannelRead(msg);
            return;
        }
        //检查黑名单
        PacModel pacMode = getPacMode(msg.dstAddr());
        if (pacMode.isBlock()) {
            log.info("{} 地址在黑名单中，拒绝连接", msg.dstAddr());
            //假装连接成功
            DefaultSocks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, socks5AddressType);
            ctx.writeAndFlush(commandResponse);
            ctx.pipeline().addLast("HttpServerCodec", new HttpServerCodec());
            ctx.pipeline().addLast(new BlackListInboundHandler());
            ctx.pipeline().remove(Socks5CommandRequestInboundHandler.class);
            ctx.pipeline().remove(Socks5CommandRequestDecoder.class);
            return;
        }
        log.debug("准备连接目标服务器，ip={},port={}", msg.dstAddr(), msg.dstPort());
        Bootstrap bootstrap = new Bootstrap();
        bootstrap = bootstrap.group(eventExecutors)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true);
        switch (ProxyModelEnum.get(configProperties.getProxyMode())) {
            case DIRECT:
                log.info("直连目标服务器，ip={},port={}", msg.dstAddr(), msg.dstPort());
                directConnect(ctx, msg, socks5AddressType, bootstrap);
                break;
            case GLOBAL:
                log.info("代理连接目标服务器，ip={},port={}", msg.dstAddr(), msg.dstPort());
                proxyConnect(ctx, msg, socks5AddressType, bootstrap);
                break;
            case PAC:
                if (pacMode.isProxy()) {
                    log.info("代理连接目标服务器，ip={},port={}", msg.dstAddr(), msg.dstPort());
                    proxyConnect(ctx, msg, socks5AddressType, bootstrap);
                } else {
                    log.info("直连目标服务器，ip={},port={}", msg.dstAddr(), msg.dstPort());
                    directConnect(ctx, msg, socks5AddressType, bootstrap);
                }
                break;
            default:
                log.error("无法支持的代理模式：{}", configProperties.getProxyMode());
                break;
        }
    }

    private void directConnect(ChannelHandlerContext ctx, DefaultSocks5CommandRequest msg, Socks5AddressType socks5AddressType, Bootstrap bootstrap) {
        ChannelFuture future;
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                //添加服务端写客户端的Handler
                ch.pipeline().addLast(new Dest2ClientInboundHandler(ctx));
            }
        });
        future = bootstrap.connect(msg.dstAddr(), msg.dstPort());
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    log.debug("目标服务器连接成功");
                    //添加客户端转发请求到服务端的Handler
                    ctx.pipeline().addLast(new Client2DestInboundHandler(future));
                    DefaultSocks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, socks5AddressType);
                    ctx.writeAndFlush(commandResponse);
                    ctx.pipeline().remove(Socks5CommandRequestInboundHandler.class);
                    ctx.pipeline().remove(Socks5CommandRequestDecoder.class);
                } else {
                    log.error("连接目标服务器失败,address={},port={}", msg.dstAddr(), msg.dstPort());
                    DefaultSocks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, socks5AddressType);
                    ctx.writeAndFlush(commandResponse);
                    future.channel().close();
                }
            }
        });
    }

    private void proxyConnect(ChannelHandlerContext ctx, DefaultSocks5CommandRequest msg, Socks5AddressType socks5AddressType, Bootstrap bootstrap) {
        ChannelFuture future;
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(SslUtil.getContext().newHandler(ch.alloc()));
                ch.pipeline().addLast(new TrojanRequestEncoder());
            }
        });
        future = bootstrap.connect(configProperties.getTrojanServerHost(), configProperties.getTrojanServerPort());
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    log.debug("代理服务器连接成功");
                    future.channel().pipeline().addLast(new TrojanDest2ClientInboundHandler(ctx));
                    //添加客户端转发请求到服务端的Handler
                    ctx.pipeline().addLast(new TrojanClient2DestInboundHandler(
                                    future,
                                    msg.dstAddr(),
                                    msg.dstPort(),
                                    ctx,
                                    socks5AddressType,
                                    configProperties.getTrojanPassword()
                            )
                    );
                    DefaultSocks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, socks5AddressType);
                    ctx.writeAndFlush(commandResponse);
                } else {
                    log.error("代理服务器连接失败,address={},port={}", msg.dstAddr(), msg.dstPort());
                    DefaultSocks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, socks5AddressType);
                    ctx.writeAndFlush(commandResponse);
                    future.channel().close();
                }
            }
        });
    }

    private PacModel getPacMode(String dstAddr) {
        for (String black : blackList.keySet()) {
            if (dstAddr.toLowerCase().endsWith(black)) {
                return blackList.get(black);
            }
        }
        //默认直连
        PacModel defaultResult = new PacModel();
        defaultResult.setProxyMode(PacModeEnum.DIRECT.mode());
        defaultResult.setDomainName(dstAddr);
        return defaultResult;
    }
}

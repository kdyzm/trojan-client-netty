package com.kdyzm.trojan.client.netty.inbound.http;

import com.kdyzm.trojan.client.netty.encoder.TrojanRequestEncoder;
import com.kdyzm.trojan.client.netty.inbound.BlackListInboundHandler;
import com.kdyzm.trojan.client.netty.inbound.RelayHandler;
import com.kdyzm.trojan.client.netty.models.HostPort;
import com.kdyzm.trojan.client.netty.router.ProxyDecision;
import com.kdyzm.trojan.client.netty.router.ProxyRouter;
import com.kdyzm.trojan.client.netty.properties.ConfigProperties;
import com.kdyzm.trojan.client.netty.util.IpUtil;
import com.kdyzm.trojan.client.netty.util.SslUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HTTP/HTTPS 本地代理：首个请求决策后建立出站连接。
 * 收敛后的线程模型（Phase 2）：出站 handler 全部在 initChannel（出站 eventLoop）静态装配；
 * 对客户端 pipeline 的增删/relay 激活/应答全部 execute 到客户端 eventLoop 串行执行，
 * 杜绝跨线程改链与 head/body 乱序。普通 HTTP 请求头编码后先入 pending 缓冲，
 * 连接就绪后与后续 HttpContent 一并按到达顺序写出，再切换为原始字节透传。
 *
 * @author kdyzm
 * @date 2021/5/14
 */
@Slf4j
public class HttpProxyInboundHandler extends SimpleChannelInboundHandler<HttpObject> {

    /**
     * 出站连接超时时间
     */
    private static final int CONNECT_TIMEOUT_MILLIS = 2000;

    private final ProxyRouter proxyRouter;

    private final ConfigProperties configProperties;

    private final EventLoopGroup eventExecutors;

    private final RelayHandler relayHandler;

    /** 首个 HttpRequest 是否已消费（keep-alive 下后续请求在隧道内字节透传，不经本 handler） */
    private final AtomicBoolean decided = new AtomicBoolean(false);

    /** 普通 HTTP 请求：编码后的请求头字节，等待连接建立期间暂存（其后 HttpContent 追加到此） */
    private ByteBuf pending;

    /** 用于把 HttpObject 重编码为原始字节（与出站目标服务器字节流对齐） */
    private EmbeddedChannel requestEncoderChannel;

    /** 连接建立是否成功（失败后 pending 释放、不再追加） */
    private boolean connectSettled;

    public HttpProxyInboundHandler(ProxyRouter proxyRouter, ConfigProperties configProperties,
                                   EventLoopGroup eventExecutors, RelayHandler relayHandler) {
        this.proxyRouter = proxyRouter;
        this.configProperties = configProperties;
        this.eventExecutors = eventExecutors;
        this.relayHandler = relayHandler;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpRequest) {
            if (decided.compareAndSet(false, true)) {
                handleFirstRequest(ctx, (HttpRequest) msg);
            }
            //同一连接上的后续 HttpRequest 理论不会出现（首个请求后 codec 被移除），防御性忽略
            return;
        }
        if (!connectSettled && pending != null && msg instanceof HttpContent) {
            //首个请求已决策、连接尚未建立：请求体编码后追加进 pending，保证与头部同序到达
            appendPending((HttpContent) msg);
        }
        //连接已建立或非本请求体：原始字节已由移除 codec 后的 relay 透传，此处无动作
    }

    private void handleFirstRequest(ChannelHandlerContext ctx, HttpRequest req) {
        HttpMethod method = req.method();
        HostPort hostPort = parseHostHeader(req.headers().get("Host"));
        String host = hostPort.getHost();
        int port = hostPort.getPort();
        ProxyDecision decision = proxyRouter.decide(host);
        if (decision == ProxyDecision.BLOCK) {
            log.info("{} 地址在黑名单中，拒绝连接", host);
            //黑名单：同线程直接改链（channelRead0 执行于客户端 eventLoop），拦截页面接管。
            //静态链的 relay 必须先于自身移除：本 handler 移除后 fireChannelRead 从原 next 继续，
            //relay 不先移除会拦在黑名单前把请求释放（与 Task 3 审查发现的 socks5 回归同类）
            ctx.pipeline().remove(RelayHandler.class);
            ctx.pipeline().addLast(new BlackListInboundHandler());
            ctx.pipeline().remove(HttpProxyInboundHandler.class);
            ctx.pipeline().fireChannelRead(req);
            return;
        }
        //初始化请求暂存：普通请求头编码入缓冲；CONNECT 无体
        if (method.equals(HttpMethod.CONNECT)) {
            if (port == -1) {
                port = 443;
            }
            pending = null;
            if (decision == ProxyDecision.PROXY) {
                proxyConnectTunnel(ctx, host, port, req.protocolVersion());
            } else {
                directConnectTunnel(ctx, host, port, req.protocolVersion());
            }
        } else {
            if (port == -1) {
                port = 80;
            }
            requestEncoderChannel = new EmbeddedChannel(new HttpRequestEncoder());
            pending = Unpooled.buffer();
            appendPending(req);
            if (decision == ProxyDecision.PROXY) {
                proxyConnectHttp(ctx, host, port);
            } else {
                directConnectHttp(ctx, host, port);
            }
        }
    }

    /** 把 HttpObject 重编码为字节并累积进 pending（HttpRequest 头部与后续 HttpContent 均按到达顺序） */
    private void appendPending(HttpObject obj) {
        if (requestEncoderChannel == null) {
            return;
        }
        requestEncoderChannel.writeOutbound(obj);
        Object encoded;
        while ((encoded = requestEncoderChannel.readOutbound()) != null) {
            ByteBuf buf = (ByteBuf) encoded;
            pending.writeBytes(buf, buf.readerIndex(), buf.readableBytes());
            buf.release();
        }
    }

    /**
     * 连接建立成功后的统一收尾（执行于客户端 eventLoop）：
     * 普通请求先写出暂存的头部字节，再切换 pipeline 为字节透传并激活 relay
     */
    private void activateRelay(ChannelHandlerContext ctx, Channel outboundChannel, boolean tunnel) {
        if (!ctx.channel().isActive()) {
            //客户端已在出站连接建立期间断开：relay 未激活，其 channelInactive 已过（无级联），
            //释放暂存数据并关闭出站连接，防止孤儿化
            log.info("客户端连接已断开，关闭出站连接并释放暂存");
            if (pending != null) {
                pending.release();
                pending = null;
            }
            if (requestEncoderChannel != null) {
                requestEncoderChannel.finishAndReleaseAll();
                requestEncoderChannel = null;
            }
            outboundChannel.close();
            return;
        }
        ChannelPipeline p = ctx.pipeline();
        p.remove(HttpServerCodec.class);
        p.remove(HttpProxyInboundHandler.class);
        if (!tunnel && pending != null) {
            outboundChannel.writeAndFlush(pending);
            pending = null;
        }
        relayHandler.activate(outboundChannel);
        if (requestEncoderChannel != null) {
            requestEncoderChannel.finishAndReleaseAll();
            requestEncoderChannel = null;
        }
    }

    /**
     * 连接失败统一处理（执行于客户端 eventLoop）：回 502、释放暂存、保持 HttpServerCodec 以编码应答
     */
    private void onConnectFailure(ChannelHandlerContext ctx, String target, int port) {
        log.error("连接目标服务器失败,host={},port={}", target, port);
        connectSettled = true;
        if (pending != null) {
            pending.release();
            pending = null;
        }
        if (requestEncoderChannel != null) {
            requestEncoderChannel.finishAndReleaseAll();
            requestEncoderChannel = null;
        }
        ctx.writeAndFlush(badGatewayResponse(HttpVersion.HTTP_1_1)).addListener(ChannelFutureListener.CLOSE);
    }

    /** 发起出站连接（普通 http 与 CONNECT 共用装配差异由调用方决定） */
    private ChannelFuture connectOutbound(String remoteHost, int remotePort, ChannelInitializer<SocketChannel> initializer) {
        Bootstrap bootstrap = new Bootstrap();
        return bootstrap.group(eventExecutors)
                .channel(NioSocketChannel.class)
                .remoteAddress(remoteHost, remotePort)
                .handler(initializer)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .connect();
    }

    private void proxyConnectTunnel(ChannelHandlerContext ctx, String host, int port, HttpVersion version) {
        log.info("[proxy][connect] {}:{}", host, port);
        int atyp = IpUtil.parseAddress(host).byteValue();
        ChannelFuture future = connectOutbound(configProperties.getTrojanServerHost(),
                configProperties.getTrojanServerPort(), new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(SslUtil.getContext().newHandler(ch.alloc()));
                        ch.pipeline().addLast(new TrojanRequestEncoder(
                                configProperties.getTrojanPassword(), atyp, host, port));
                        ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                    }
                });
        future.addListener((ChannelFuture f) -> {
            Channel clientChannel = ctx.channel();
            if (f.isSuccess()) {
                clientChannel.eventLoop().execute(() -> {
                    //先回 200（浏览器收到后才发送 TLS 数据），随后切换透传
                    DefaultFullHttpResponse ok = new DefaultFullHttpResponse(version, new HttpResponseStatus(200, "OK"));
                    ctx.writeAndFlush(ok).addListener((ChannelFuture done) ->
                            clientChannel.eventLoop().execute(() -> activateRelay(ctx, f.channel(), true)));
                });
            } else {
                clientChannel.eventLoop().execute(() -> onConnectFailure(ctx,
                        configProperties.getTrojanServerHost(), configProperties.getTrojanServerPort()));
            }
        });
    }

    private void directConnectTunnel(ChannelHandlerContext ctx, String host, int port, HttpVersion version) {
        log.info("[direct][connect] {}:{}", host, port);
        ChannelFuture future = connectOutbound(host, port, new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new RelayHandler(ctx.channel()));
            }
        });
        future.addListener((ChannelFuture f) -> {
            Channel clientChannel = ctx.channel();
            if (f.isSuccess()) {
                clientChannel.eventLoop().execute(() -> {
                    DefaultFullHttpResponse ok = new DefaultFullHttpResponse(version, new HttpResponseStatus(200, "OK"));
                    ctx.writeAndFlush(ok).addListener((ChannelFuture done) ->
                            clientChannel.eventLoop().execute(() -> activateRelay(ctx, f.channel(), true)));
                });
            } else {
                clientChannel.eventLoop().execute(() -> onConnectFailure(ctx, host, port));
            }
        });
    }

    private void proxyConnectHttp(ChannelHandlerContext ctx, String host, int port) {
        log.info("[proxy][http] {}:{}", host, port);
        int atyp = IpUtil.parseAddress(host).byteValue();
        ChannelFuture future = connectOutbound(configProperties.getTrojanServerHost(),
                configProperties.getTrojanServerPort(), new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(SslUtil.getContext().newHandler(ch.alloc()));
                        ch.pipeline().addLast(new TrojanRequestEncoder(
                                configProperties.getTrojanPassword(), atyp, host, port));
                        ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                    }
                });
        future.addListener((ChannelFuture f) -> {
            Channel clientChannel = ctx.channel();
            if (f.isSuccess()) {
                clientChannel.eventLoop().execute(() -> activateRelay(ctx, f.channel(), false));
            } else {
                clientChannel.eventLoop().execute(() -> onConnectFailure(ctx,
                        configProperties.getTrojanServerHost(), configProperties.getTrojanServerPort()));
            }
        });
    }

    private void directConnectHttp(ChannelHandlerContext ctx, String host, int port) {
        log.info("[direct][http] {}:{}", host, port);
        ChannelFuture future = connectOutbound(host, port, new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new RelayHandler(ctx.channel()));
            }
        });
        future.addListener((ChannelFuture f) -> {
            Channel clientChannel = ctx.channel();
            if (f.isSuccess()) {
                clientChannel.eventLoop().execute(() -> activateRelay(ctx, f.channel(), false));
            } else {
                clientChannel.eventLoop().execute(() -> onConnectFailure(ctx, host, port));
            }
        });
    }

    /**
     * 构造 502 Bad Gateway 响应：代理无法连接上游时返回给浏览器，替代直接断开
     *
     * @param version 响应使用的 HTTP 版本（当前调用方统一 HTTP_1_1）
     */
    static FullHttpResponse badGatewayResponse(HttpVersion version) {
        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(version, HttpResponseStatus.BAD_GATEWAY);
        resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        return resp;
    }

    /**
     * 解析 Host 头为 host 与端口。
     * 支持 IPv6 字面量 [::1]:443 形式（修复 split(":") 解析错乱）；
     * port 缺失或非法时返回 -1，由调用方按协议默认（CONNECT→443，其余→80）。
     *
     * @param header Host 头原始值（可为 null）
     */
    static HostPort parseHostHeader(String header) {
        if (header == null) {
            return new HostPort("", -1);
        }
        String trimmed = header.trim();
        if (trimmed.isEmpty()) {
            return new HostPort("", -1);
        }
        if (trimmed.startsWith("[")) {
            //IPv6 字面量：host 在 [] 内，其后可选 :port
            int closeBracket = trimmed.indexOf(']');
            if (closeBracket < 0) {
                return new HostPort("", -1);
            }
            String host = trimmed.substring(1, closeBracket);
            String rest = trimmed.substring(closeBracket + 1);
            int port = rest.startsWith(":") ? parsePort(rest.substring(1)) : -1;
            return new HostPort(host, port);
        }
        int colon = trimmed.lastIndexOf(':');
        if (colon < 0) {
            return new HostPort(trimmed, -1);
        }
        return new HostPort(trimmed.substring(0, colon), parsePort(trimmed.substring(colon + 1)));
    }

    private static int parsePort(String portStr) {
        try {
            int port = Integer.parseInt(portStr);
            return port > 0 && port <= 65535 ? port : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

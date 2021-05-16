package com.kdyzm.trojan.client.netty.inbound.http;

import com.kdyzm.trojan.client.netty.encoder.TrojanRequestEncoder;
import com.kdyzm.trojan.client.netty.enums.PacModeEnum;
import com.kdyzm.trojan.client.netty.enums.ProxyModelEnum;
import com.kdyzm.trojan.client.netty.inbound.BlackListInboundHandler;
import com.kdyzm.trojan.client.netty.inbound.TrojanClient2DestInboundHandler;
import com.kdyzm.trojan.client.netty.inbound.TrojanDest2ClientInboundHandler;
import com.kdyzm.trojan.client.netty.models.PacModel;
import com.kdyzm.trojan.client.netty.properties.ConfigProperties;
import com.kdyzm.trojan.client.netty.util.IpUtil;
import com.kdyzm.trojan.client.netty.util.SslUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * @author kdyzm
 * @date 2021/5/14
 */
@Slf4j
public class HttpProxyInboundHandler extends SimpleChannelInboundHandler<HttpObject> {

    private Map<String, PacModel> blackList;

    private ConfigProperties configProperties;

    private EventLoopGroup eventExecutors;

    public HttpProxyInboundHandler(Map<String, PacModel> blackList, ConfigProperties configProperties, EventLoopGroup eventExecutors) {
        this.blackList = blackList;
        this.configProperties = configProperties;
        this.eventExecutors = eventExecutors;
    }

    /**
     * 保留全局ctx
     */
    private ChannelHandlerContext ctx;

    /**
     * channelActive方法中将ctx保留为全局变量
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.ctx = ctx;
    }

    /**
     * Complete方法中刷新数据
     *
     * @param ctx
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    private PacModel getPacMode(String dstAddr) {
        for (String black : blackList.keySet()) {
            if (dstAddr.toLowerCase().endsWith(black)) {
                return blackList.get(black);
            }
        }

        //如果不在pac名单中，默认行为取决于代理模式
        PacModel defaultResult = new PacModel();
        ProxyModelEnum proxyModelEnum = ProxyModelEnum.get(configProperties.getProxyMode());
        switch (proxyModelEnum) {
            case PAC:
                defaultResult.setProxyMode(PacModeEnum.DIRECT.mode());
                break;
            case GLOBAL:
            default:
                defaultResult.setProxyMode(PacModeEnum.PROXY.mode());
                break;
        }

        defaultResult.setDomainName(dstAddr);
        return defaultResult;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, HttpObject msg) throws Exception {
        if (!(msg instanceof HttpRequest)) {
            return;
        }
        //转成 HttpRequest
        HttpRequest req = (HttpRequest) msg;
        //获取请求方式，http的有get post ...， https的是 CONNECT
        HttpMethod method = req.method();
        //获取请求头中的Host字段
        String headerHost = req.headers().get("Host");
        String host = "";
        //端口默认80
        int port = -1;
        //可能有请求是 host:port的情况，
        String[] split = headerHost.split(":");
        host = split[0];
        if (split.length > 1) {
            port = Integer.parseInt(split[1]);
        }
        //检查黑名单
        PacModel pacMode = getPacMode(host);
        if (pacMode.isBlock()) {
            log.info("{} 地址在黑名单中，拒绝连接", host);
            //假装连接成功
            ctx.pipeline().addLast(new BlackListInboundHandler());
            ctx.pipeline().remove(HttpProxyInboundHandler.class);
            ctx.pipeline().fireChannelRead(msg);
            return;
        }

        switch (ProxyModelEnum.get(configProperties.getProxyMode())) {
            case DIRECT:
                directConnect(host, port, method, req);
                break;
            case GLOBAL:
            case PAC:
                if (pacMode.isProxy()) {
                    proxyConnect(host, port, method, req);
                } else {
                    directConnect(host, port, method, req);
                }
                break;
            default:
                log.error("无法支持的代理模式：{}", configProperties.getProxyMode());
                break;
        }
    }

    private void proxyConnect(String host, int port, HttpMethod method, HttpRequest req) {

        //根据host和port创建连接到服务器的连接
        //如果是https的连接

        if (method.equals(HttpMethod.CONNECT)) {
            if (port == -1) {
                port = 443;
            }
            log.info("[proxy][connect] {}:{}", host, port);
            Bootstrap bootstrap = new Bootstrap();
            ChannelFuture future = bootstrap.group(eventExecutors)
                    .channel(NioSocketChannel.class)
                    .remoteAddress(configProperties.getTrojanServerHost(), configProperties.getTrojanServerPort())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(SslUtil.getContext().newHandler(ch.alloc()));
                            ch.pipeline().addLast(new TrojanRequestEncoder());
                        }
                    })
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                    .connect();
            int finalPort = port;
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    if (channelFuture.isSuccess()) {
                        //首先向浏览器发送一个200的响应，证明已经连接成功了，可以发送数据了
                        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(200, "OK"));
                        //向浏览器发送同意连接的响应，并在发送完成后移除httpcode和httpservice两个handler
                        ctx.writeAndFlush(resp).addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                ChannelPipeline p = ctx.pipeline();
                                p.remove(HttpServerCodec.class);
                                p.remove(HttpProxyInboundHandler.class);
                            }
                        });
                        future.channel().pipeline().addLast(new TrojanDest2ClientInboundHandler(ctx));
                        //添加客户端转发请求到服务端的Handler
                        ChannelPipeline p = ctx.pipeline();
                        p.addLast(new TrojanClient2DestInboundHandler(
                                        future,
                                        host,
                                        finalPort,
                                        IpUtil.parseAddress(host),
                                        configProperties.getTrojanPassword()
                                )
                        );
                    } else {
                        ctx.close();
                        channelFuture.cancel(true);
                    }
                }
            });
        } else {
            if (port == -1) {
                port = 80;
            }
            log.info("[proxy][http] {}:{}", host, port);
            //如果是http连接，首先将接受的请求转换成原始字节数据
            EmbeddedChannel em = new EmbeddedChannel(new HttpRequestEncoder());
            em.writeOutbound(req);
            final Object msg = em.readOutbound();
            em.close();
            Bootstrap bootstrap = new Bootstrap();
            ChannelFuture future = bootstrap.group(eventExecutors)
                    .channel(NioSocketChannel.class)
                    .remoteAddress(configProperties.getTrojanServerHost(), configProperties.getTrojanServerPort())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(SslUtil.getContext().newHandler(ch.alloc()));
                            ch.pipeline().addLast(new TrojanRequestEncoder());
                        }
                    })
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                    .connect();
            int finalPort1 = port;
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    ChannelPipeline p = ctx.pipeline();
                    p.remove(HttpServerCodec.class);
                    p.remove(HttpProxyInboundHandler.class);
                    future.channel().pipeline().addLast(new TrojanDest2ClientInboundHandler(ctx));
                    //添加客户端转发请求到服务端的Handler
                    ctx.pipeline().addLast(new TrojanClient2DestInboundHandler(
                                    future,
                                    host,
                                    finalPort1,
                                    IpUtil.parseAddress(host),
                                    configProperties.getTrojanPassword()
                            )
                    );
                    ctx.pipeline().fireChannelRead(msg);
                }
            });
        }
    }

    private void directConnect(String host, int port, HttpMethod method, HttpRequest req) {
        //根据host和port创建连接到服务器的连接
        //根据是http还是http的不同，为promise添加不同的监听器
        if (method.equals(HttpMethod.CONNECT)) {
            if (port == -1) {
                port = 443;
            }
            Promise<Channel> promise = createPromise(host, port);
            log.info("[direct][connect] {}:{}", host, port);
            //如果是https的连接
            promise.addListener(new FutureListener<Channel>() {
                @Override
                public void operationComplete(Future<Channel> channelFuture) throws Exception {
                    //首先向浏览器发送一个200的响应，证明已经连接成功了，可以发送数据了
                    FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(200, "OK"));
                    //向浏览器发送同意连接的响应，并在发送完成后移除httpcode和httpservice两个handler
                    ctx.writeAndFlush(resp).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            ChannelPipeline p = ctx.pipeline();
                            p.remove(HttpServerCodec.class);
                            p.remove(HttpProxyInboundHandler.class);
                        }
                    });;
                    ChannelPipeline p = ctx.pipeline();
                    //将客户端channel添加到转换数据的channel，（这个NoneHandler是自己写的）
                    p.addLast(new ForwardInboundHandler(channelFuture.getNow()));
                }
            });
        } else {
            if (port == -1) {
                port = 80;
            }
            Promise<Channel> promise = createPromise(host, port);
            log.info("[direct][http] {}:{}", host, port);
            //如果是http连接，首先将接受的请求转换成原始字节数据
            EmbeddedChannel em = new EmbeddedChannel(new HttpRequestEncoder());
            em.writeOutbound(req);
            final Object o = em.readOutbound();
            em.close();
            promise.addListener(new FutureListener<Channel>() {
                @Override
                public void operationComplete(Future<Channel> channelFuture) throws Exception {
                    //移除	httpcode	httpservice 并添加	NoneHandler，并向服务器发送请求的byte数据				
                    ChannelPipeline p = ctx.pipeline();
                    p.remove(HttpServerCodec.class);
                    p.remove(HttpProxyInboundHandler.class);
                    //添加handler
                    p.addLast(new ForwardInboundHandler(channelFuture.getNow()));
                    channelFuture.get().writeAndFlush(o);
                }
            });
        }
    }


    /**
     * 根据host和端口，创建一个连接web的连接
     */
    private Promise<Channel> createPromise(String host, int port) {
        final Promise<Channel> promise = ctx.executor().newPromise();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .remoteAddress(host, port)
                .handler(new ForwardInboundHandler(ctx.channel()))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .connect()
                .addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture channelFuture) throws Exception {
                        if (channelFuture.isSuccess()) {
                            promise.setSuccess(channelFuture.channel());
                        } else {
                            ctx.close();
                            channelFuture.cancel(true);
                        }
                    }
                });
        return promise;
    }


}
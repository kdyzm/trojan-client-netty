package com.kdyzm.trojan.client.netty.server;

import com.kdyzm.trojan.client.netty.inbound.RelayHandler;
import com.kdyzm.trojan.client.netty.monitor.ConnectionTrafficHandler;
import com.kdyzm.trojan.client.netty.monitor.TrafficMonitor;
import com.kdyzm.trojan.client.netty.inbound.Socks5CommandRequestInboundHandler;
import com.kdyzm.trojan.client.netty.inbound.Socks5InitialRequestInboundHandler;
import com.kdyzm.trojan.client.netty.inbound.Socks5PasswordAuthRequestInboundHandler;
import com.kdyzm.trojan.client.netty.inbound.http.HttpAuthInboundHandler;
import com.kdyzm.trojan.client.netty.inbound.http.HttpProxyInboundHandler;
import com.kdyzm.trojan.client.netty.properties.ConfigProperties;
import com.kdyzm.trojan.client.netty.properties.ConfigUtil;
import com.kdyzm.trojan.client.netty.router.ProxyRouter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * @author kdyzm
 * @date 2021/5/14
 */
@Slf4j
public class NettyServerInitializer extends ChannelInitializer<SocketChannel> {

    private final ConfigProperties configProperties;

    private final ConfigUtil configUtil;

    private final EventLoopGroup clientWorkGroup;

    private final TrafficMonitor trafficMonitor;

    public NettyServerInitializer(EventLoopGroup clientWorkGroup, ConfigProperties configProperties,
                                  ConfigUtil configUtil, TrafficMonitor trafficMonitor) {
        this.configProperties = configProperties;
        this.configUtil = configUtil;
        this.clientWorkGroup = clientWorkGroup;
        this.trafficMonitor = trafficMonitor;
    }

    /**
     * 根据不同的端口号创建不同的pipeline
     *
     * @param ch
     * @throws Exception
     */
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        int localPort = ch.localAddress().getPort();
        ChannelPipeline pipeline = ch.pipeline();
        //处理socks5协议
        if (localPort == configProperties.getSocks5Port()) {
            //链头度量：统计双向原始字节并注册连接监控
            pipeline.addLast(new ConnectionTrafficHandler(trafficMonitor));
            //全空闲超时（读/写任一活跃即不算空闲）：relay 收到 IdleStateEvent 后双向回收（握手期与透传期共用 300s）。
            //不能只统计读空闲：下行数据（服务器→浏览器）在客户端腿上表现为写，reader-idle 会误切断活跃下载/流
            pipeline.addLast(new IdleStateHandler(0, 0, 300));

            //socks5响应最后一个encode
            pipeline.addLast(Socks5ServerEncoder.DEFAULT);

            //处理socks5初始化请求
            pipeline.addLast(new Socks5InitialRequestDecoder());
            pipeline.addLast(new Socks5InitialRequestInboundHandler(configProperties));

            //处理认证请求
            if (configProperties.isAuthentication()) {
                pipeline.addLast(new Socks5PasswordAuthRequestDecoder());
                pipeline.addLast(new Socks5PasswordAuthRequestInboundHandler(configUtil));
            }
            //处理connection请求：决策 + 建立出站连接；relay 常驻链尾，激活后接管业务数据
            ProxyRouter proxyRouter = new ProxyRouter(configUtil.getPacModelMap(), configProperties.getProxyMode());
            RelayHandler relayHandler = new RelayHandler();
            pipeline.addLast(new Socks5CommandRequestDecoder());
            pipeline.addLast(new Socks5CommandRequestInboundHandler(clientWorkGroup, proxyRouter, configProperties, relayHandler));
            pipeline.addLast(relayHandler);
            //处理http协议
        } else {
            //链头度量：统计双向原始字节并注册连接监控
            pipeline.addLast(new ConnectionTrafficHandler(trafficMonitor));
            //全空闲超时（读/写任一活跃即不算空闲）：透传期由 relay 双向回收；读与写都静默 300s 才回收，
            //下行下载流在客户端腿上是写，只统计读会误回收活跃连接
            pipeline.addLast(new IdleStateHandler(0, 0, 300));
            pipeline.addLast(new HttpServerCodec());
            //http 代理认证（可选）：Proxy-Authorization Basic，凭据复用 users.properties
            if (configProperties.isHttpAuthentication()) {
                pipeline.addLast(new HttpAuthInboundHandler(configUtil.getUsers()));
            }
            ProxyRouter proxyRouter = new ProxyRouter(configUtil.getPacModelMap(), configProperties.getProxyMode());
            RelayHandler relayHandler = new RelayHandler();
            pipeline.addLast(new HttpProxyInboundHandler(proxyRouter, configProperties, clientWorkGroup, relayHandler));
            pipeline.addLast(relayHandler);
        }
    }
}

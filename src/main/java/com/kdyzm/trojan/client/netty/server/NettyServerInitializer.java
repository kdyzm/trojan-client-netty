package com.kdyzm.trojan.client.netty.server;

import com.kdyzm.trojan.client.netty.inbound.Socks5CommandRequestInboundHandler;
import com.kdyzm.trojan.client.netty.inbound.Socks5InitialRequestInboundHandler;
import com.kdyzm.trojan.client.netty.inbound.Socks5PasswordAuthRequestInboundHandler;
import com.kdyzm.trojan.client.netty.inbound.http.HttpProxyInboundHandler;
import com.kdyzm.trojan.client.netty.properties.ConfigProperties;
import com.kdyzm.trojan.client.netty.properties.ConfigUtil;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
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

    public NettyServerInitializer(EventLoopGroup clientWorkGroup, ConfigProperties configProperties, ConfigUtil configUtil) {
        this.configProperties = configProperties;
        this.configUtil = configUtil;
        this.clientWorkGroup = clientWorkGroup;
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
            //处理connection请求
            pipeline.addLast(new Socks5CommandRequestDecoder());
            pipeline.addLast(new Socks5CommandRequestInboundHandler(clientWorkGroup, configUtil.getPacModelMap(), configProperties));
            //处理http协议
        } else {
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpProxyInboundHandler());
        }
    }
}

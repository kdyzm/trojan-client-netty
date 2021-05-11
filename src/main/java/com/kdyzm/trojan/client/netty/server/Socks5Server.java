package com.kdyzm.trojan.client.netty.server;

import com.kdyzm.trojan.client.netty.inbound.Socks5CommandRequestInboundHandler;
import com.kdyzm.trojan.client.netty.inbound.Socks5InitialRequestInboundHandler;
import com.kdyzm.trojan.client.netty.inbound.Socks5PasswordAuthRequestInboundHandler;
import com.kdyzm.trojan.client.netty.properties.ConfigProperties;
import com.kdyzm.trojan.client.netty.properties.ConfigUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author kdyzm
 * @date 2021-04-24
 */
@Slf4j
@Component
@AllArgsConstructor
public class Socks5Server {

    private final ConfigProperties configProperties;

    private final ConfigUtil configUtil;

    public void start() throws InterruptedException {
        EventLoopGroup clientWorkGroup = new NioEventLoopGroup();
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        try {
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 512)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();

                            //socks5响应最后一个encode
                            pipeline.addLast(Socks5ServerEncoder.DEFAULT);

                            //处理socks5初始化请求
                            pipeline.addLast(new Socks5InitialRequestDecoder());
                            pipeline.addLast(new Socks5InitialRequestInboundHandler(configProperties));

                            //处理认证请求
                            if(configProperties.isAuthentication()){
                                pipeline.addLast(new Socks5PasswordAuthRequestDecoder());
                                pipeline.addLast(new Socks5PasswordAuthRequestInboundHandler(configUtil));
                            }
                            //处理connection请求
                            pipeline.addLast(new Socks5CommandRequestDecoder());
                            pipeline.addLast(new Socks5CommandRequestInboundHandler(clientWorkGroup, configUtil.getPacModelMap(),configProperties));
                        }
                    });
            ChannelFuture future = bootstrap.bind(configProperties.getServerPort()).sync();
            log.info("socks5 netty server has started on port {}", configProperties.getServerPort());
            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}

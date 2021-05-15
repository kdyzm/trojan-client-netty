package com.kdyzm.trojan.client.netty.server;

import com.kdyzm.trojan.client.netty.properties.ConfigProperties;
import com.kdyzm.trojan.client.netty.properties.ConfigUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author kdyzm
 * @date 2021/5/14
 */
@Component
@Slf4j
@AllArgsConstructor
public class NettyServer {

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
                    .childHandler(new NettyServerInitializer(clientWorkGroup, configProperties, configUtil));
            ChannelFuture socks5Future = bootstrap.bind(configProperties.getSocks5Port()).sync();
            log.info("socks5 netty server has started on port {}", configProperties.getSocks5Port());
            ChannelFuture httpFuture = bootstrap.bind(configProperties.getHttpPort()).sync();
            log.info("http netty server has started on port {}", configProperties.getHttpPort());
            socks5Future.channel().closeFuture().sync();
            httpFuture.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}

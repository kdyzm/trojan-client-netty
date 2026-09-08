package com.kdyzm.trojan.client.netty.server;

import com.kdyzm.trojan.client.netty.properties.ConfigProperties;
import com.kdyzm.trojan.client.netty.properties.ConfigUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * socks5 与 http 两个本地代理端口的服务器。
 * 实现 DisposableBean：Spring 容器关闭（含 JVM shutdown hook）时关闭 accept channel
 * 并优雅停掉三个 EventLoopGroup（含专用于出站连接的 clientWorkGroup）。
 *
 * @author kdyzm
 * @date 2021/5/14
 */
@Component
@Slf4j
public class NettyServer implements DisposableBean {

    private final ConfigProperties configProperties;

    private final ConfigUtil configUtil;

    private EventLoopGroup clientWorkGroup;

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    /** 已 bind 的 server channel，destroy 时逐一关闭以解除 closeFuture 阻塞 */
    private final List<Channel> serverChannels = new ArrayList<>();

    public NettyServer(ConfigProperties configProperties, ConfigUtil configUtil) {
        this.configProperties = configProperties;
        this.configUtil = configUtil;
    }

    public void start() throws InterruptedException {
        clientWorkGroup = new NioEventLoopGroup();
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
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
            serverChannels.add(socks5Future.channel());
            serverChannels.add(httpFuture.channel());
            socks5Future.channel().closeFuture().sync();
            httpFuture.channel().closeFuture().sync();
        } finally {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
        }
    }

    /**
     * Spring 容器关闭回调（shutdown hook 触发 ctx.close 时执行）：
     * 关闭 accept channel 使 start() 的 closeFuture sync 返回，再停掉出站连接专用线程组
     */
    @Override
    public void destroy() {
        log.info("netty server is shutting down ......");
        for (Channel serverChannel : serverChannels) {
            serverChannel.close();
        }
        if (clientWorkGroup != null) {
            clientWorkGroup.shutdownGracefully();
        }
    }
}

package com.kdyzm.trojan.client.netty.server;

import com.kdyzm.trojan.client.netty.inbound.http.HttpProxyInboundHandler;
import com.kdyzm.trojan.client.netty.properties.ConfigProperties;
import com.kdyzm.trojan.client.netty.properties.ConfigUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 已经整合进了NettyServer
 *
 * @author kdyzm
 * @date 2021/5/14
 * @see NettyServer
 */
@Component
@AllArgsConstructor
@Slf4j
@Deprecated
public class HttpServer {

    private final ConfigUtil configUtil;

    private final ConfigProperties configProperties;

    public void start() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workGroup = new NioEventLoopGroup();
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 6000)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast("httpcode", new HttpServerCodec());
                            p.addLast("httpservice", new HttpProxyInboundHandler(configUtil.getPacModelMap(), configProperties, eventLoopGroup));
                        }
                    });

            ChannelFuture f = b.bind(1082).sync();
            log.info("http netty server has started on port {}", 1082);
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            workGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

}

package com.kdyzm.socks5.netty;

import com.kdyzm.socks5.netty.config.Config;
import com.kdyzm.socks5.netty.inbound.Socks5CommandRequestInboundHandler;
import com.kdyzm.socks5.netty.inbound.Socks5InitialRequestInboundHandler;
import com.kdyzm.socks5.netty.server.Socks5Server;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author kdyzm
 * @date 2021-04-23
 */
@Slf4j
public class Main {

    public static void main(String[] args) throws InterruptedException {
        log.info("socks5 netty server is starting ......");
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(Config.class);
        ctx.refresh();

        Socks5Server server = ctx.getBean(Socks5Server.class);
        server.start();
    }
}

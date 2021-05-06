package com.kdyzm.trojan.client.netty;

import com.kdyzm.trojan.client.netty.config.Config;
import com.kdyzm.trojan.client.netty.server.Socks5Server;
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

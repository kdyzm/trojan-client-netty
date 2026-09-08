package com.kdyzm.trojan.client.netty;

import com.kdyzm.trojan.client.netty.config.Config;
import com.kdyzm.trojan.client.netty.server.NettyServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author kdyzm
 * @date 2021-04-23
 */
@Slf4j
public class Main {

    public static void main(String[] args) {
        log.info("proxy netty server is starting ......");
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(Config.class);
            //注册 shutdown hook：Ctrl+C / kill 时优雅关闭（关闭 accept channel 并停掉各 EventLoopGroup）
            ctx.registerShutdownHook();
            ctx.refresh();

            NettyServer server = ctx.getBean(NettyServer.class);
            server.start();
        } catch (Exception e) {
            log.error("代理服务器启动失败，请检查端口是否被占用、config.yml 配置是否正确", e);
            System.exit(1);
        }
    }
}

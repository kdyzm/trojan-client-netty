package com.kdyzm.trojan.client.netty.monitor;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;

/**
 * 监控端口 pipeline：纯 HTTP 服务（页面 + JSON API），与代理流量完全隔离
 */
public class MonitorServerInitializer extends ChannelInitializer<SocketChannel> {

    private final TrafficMonitor monitor;

    public MonitorServerInitializer(TrafficMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast(new HttpServerCodec());
        ch.pipeline().addLast(new MonitorHttpHandler(monitor));
    }
}

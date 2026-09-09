package com.kdyzm.trojan.client.netty.monitor;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;

/**
 * 链头度量 handler：统计客户端 channel 双向原始字节并管理连接注册。
 * 位置必须在任何编解码器之前——inbound 原始字节与 outbound 编码后字节均经过此处，
 * 天然覆盖握手期与业务透传期。决策 handler 建连后经 {@link #COUNTER_KEY}
 * 读取计数器并调用 setTarget 补全目标元数据。
 *
 * @author kdyzm
 * @date 2026-09-09
 */
@Slf4j
public class ConnectionTrafficHandler extends ChannelDuplexHandler {

    /** 决策 handler 读取的连接计数器通道 */
    public static final AttributeKey<TrafficCounter> COUNTER_KEY = AttributeKey.valueOf("trafficCounter");

    private final TrafficMonitor monitor;

    private TrafficCounter counter;

    public ConnectionTrafficHandler(TrafficMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        String src = "";
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress) {
            src = ((InetSocketAddress) ctx.channel().remoteAddress()).toString();
        }
        counter = monitor.createCounter(src);
        ctx.channel().attr(COUNTER_KEY).set(counter);
        ctx.fireChannelActive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf && counter != null) {
            counter.addUp(((ByteBuf) msg).readableBytes());
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf && counter != null) {
            counter.addDown(((ByteBuf) msg).readableBytes());
        }
        ctx.write(msg, promise);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (counter != null) {
            monitor.removeCounter(counter.getId());
        }
        ctx.fireChannelInactive();
    }
}

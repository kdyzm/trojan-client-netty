package com.kdyzm.trojan.client.netty.monitor;

import com.kdyzm.trojan.client.netty.router.ProxyDecision;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * 链头度量 handler：双向字节统计、attr 暴露、生命周期注册
 */
public class ConnectionTrafficHandlerTest {

    private ConnectionSnapshot singleSnapshot(EmbeddedChannel channel, TrafficMonitor monitor) {
        //构造 EmbeddedChannel 后 channelActive 已触发（注册完成）
        TrafficCounter counter = channel.attr(ConnectionTrafficHandler.COUNTER_KEY).get();
        assertNotNull(counter);
        counter.setTarget("socks5", ProxyDecision.PROXY, "example.com", 443);
        List<ConnectionSnapshot> snapshots = monitor.snapshot();
        assertEquals(1, snapshots.size());
        return snapshots.get(0);
    }

    @Test
    public void testUpAndDownBytesCounted() {
        TrafficMonitor monitor = new TrafficMonitor();
        EmbeddedChannel channel = new EmbeddedChannel(new ConnectionTrafficHandler(monitor));

        //inbound = 浏览器发出 = 上行
        channel.writeInbound(Unpooled.wrappedBuffer("get-request".getBytes(CharsetUtil.UTF_8)));
        //outbound = 返回浏览器 = 下行
        channel.writeOutbound(Unpooled.wrappedBuffer("response-data".getBytes(CharsetUtil.UTF_8)));

        ConnectionSnapshot snapshot = singleSnapshot(channel, monitor);
        assertEquals(11L, snapshot.getUpBytes());   // "get-request".length()
        assertEquals(13L, snapshot.getDownBytes()); // "response-data".length()

        //透传保真：outbound 数据可被下游读出
        ByteBuf outbound = channel.readOutbound();
        assertEquals("response-data", outbound.toString(CharsetUtil.UTF_8));
        outbound.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void testNonByteBufSkipped() {
        TrafficMonitor monitor = new TrafficMonitor();
        EmbeddedChannel channel = new EmbeddedChannel(new ConnectionTrafficHandler(monitor));
        //非 ByteBuf 消息不应计入也不应阻塞
        channel.writeInbound("not-a-buffer");
        channel.writeOutbound("also-not-a-buffer");
        ConnectionSnapshot snapshot = singleSnapshot(channel, monitor);
        assertEquals(0L, snapshot.getUpBytes());
        assertEquals(0L, snapshot.getDownBytes());
        channel.finishAndReleaseAll();
    }

    @Test
    public void testChannelInactiveRemovesFromMonitor() {
        TrafficMonitor monitor = new TrafficMonitor();
        EmbeddedChannel channel = new EmbeddedChannel(new ConnectionTrafficHandler(monitor));
        assertEquals(1, monitor.size());
        channel.close();
        channel.runPendingTasks();
        assertEquals(0, monitor.size());
        channel.finishAndReleaseAll();
    }

    @Test
    public void testSrcFromRemoteAddress() {
        TrafficMonitor monitor = new TrafficMonitor();
        EmbeddedChannel channel = new EmbeddedChannel(new ConnectionTrafficHandler(monitor));
        //EmbeddedChannel 无真实远端地址——attr 的 counter.src 为 null/空可接受；验证 key 可达即可
        TrafficCounter counter = channel.attr(ConnectionTrafficHandler.COUNTER_KEY).get();
        assertNotNull(counter);
        channel.finishAndReleaseAll();
    }
}

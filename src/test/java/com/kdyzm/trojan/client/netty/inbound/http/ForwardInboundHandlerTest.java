package com.kdyzm.trojan.client.netty.inbound.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * HTTP 直连透传 handler 的连接生命周期管理测试：
 * 任一侧断开，另一侧连接应被级联关闭，数据转发方向正确
 */
public class ForwardInboundHandlerTest {

    /**
     * 构造一对互相指向的 EmbeddedChannel，模拟浏览器侧(client)与目标站侧(server)
     */
    private EmbeddedChannel[] newRelayPair() {
        // 注意：先建一个占位 channel 会让结构别扭，这里用两段式构造
        EmbeddedChannel server = new EmbeddedChannel();
        EmbeddedChannel client = new EmbeddedChannel(new ForwardInboundHandler(server));
        server.pipeline().addLast(new ForwardInboundHandler(client));
        return new EmbeddedChannel[]{client, server};
    }

    @Test
    public void testDataForwardedClientToServer() {
        EmbeddedChannel[] pair = newRelayPair();
        pair[0].writeInbound(Unpooled.wrappedBuffer("hello".getBytes()));
        ByteBuf received = pair[1].readOutbound();
        assertEquals("hello", received.toString(io.netty.util.CharsetUtil.UTF_8));
        received.release();
        pair[0].finishAndReleaseAll();
        pair[1].finishAndReleaseAll();
    }

    @Test
    public void testDataForwardedServerToClient() {
        EmbeddedChannel[] pair = newRelayPair();
        pair[1].writeInbound(Unpooled.wrappedBuffer("world".getBytes()));
        ByteBuf received = pair[0].readOutbound();
        assertEquals("world", received.toString(io.netty.util.CharsetUtil.UTF_8));
        received.release();
        pair[0].finishAndReleaseAll();
        pair[1].finishAndReleaseAll();
    }

    @Test
    public void testClientCloseCascadesToServer() {
        EmbeddedChannel[] pair = newRelayPair();
        pair[0].close();
        pair[0].runPendingTasks();
        pair[1].runPendingTasks();
        assertFalse("浏览器侧关闭后，目标站连接应被级联关闭", pair[1].isOpen());
        pair[1].finishAndReleaseAll();
    }

    @Test
    public void testServerCloseCascadesToClient() {
        EmbeddedChannel[] pair = newRelayPair();
        pair[1].close();
        pair[0].runPendingTasks();
        pair[1].runPendingTasks();
        assertFalse("目标站侧关闭后，浏览器连接应被级联关闭", pair[0].isOpen());
        pair[0].finishAndReleaseAll();
    }
}

package com.kdyzm.trojan.client.netty.inbound;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * 统一双向透传 handler 测试：转发、级联关闭、激活前丢弃、空闲回收
 */
public class RelayHandlerTest {

    /** 客户端侧激活式 relay + 出站侧固定式 relay 配对 */
    private EmbeddedChannel[] newActivatedPair() {
        EmbeddedChannel server = new EmbeddedChannel();
        RelayHandler clientRelay = new RelayHandler();
        EmbeddedChannel client = new EmbeddedChannel(clientRelay);
        server.pipeline().addLast(new RelayHandler(client));
        clientRelay.activate(server);
        return new EmbeddedChannel[]{client, server};
    }

    @Test
    public void testDataForwardedClientToServer() {
        EmbeddedChannel[] pair = newActivatedPair();
        pair[0].writeInbound(Unpooled.wrappedBuffer("hello".getBytes()));
        ByteBuf received = pair[1].readOutbound();
        assertEquals("hello", received.toString(io.netty.util.CharsetUtil.UTF_8));
        received.release();
        pair[0].finishAndReleaseAll();
        pair[1].finishAndReleaseAll();
    }

    @Test
    public void testDataForwardedServerToClient() {
        EmbeddedChannel[] pair = newActivatedPair();
        pair[1].writeInbound(Unpooled.wrappedBuffer("world".getBytes()));
        ByteBuf received = pair[0].readOutbound();
        assertEquals("world", received.toString(io.netty.util.CharsetUtil.UTF_8));
        received.release();
        pair[0].finishAndReleaseAll();
        pair[1].finishAndReleaseAll();
    }

    @Test
    public void testClientCloseCascadesToServer() {
        EmbeddedChannel[] pair = newActivatedPair();
        pair[0].close();
        pair[0].runPendingTasks();
        pair[1].runPendingTasks();
        assertFalse("客户端侧关闭后服务端连接应级联关闭", pair[1].isOpen());
        pair[1].finishAndReleaseAll();
    }

    @Test
    public void testServerCloseCascadesToClient() {
        EmbeddedChannel[] pair = newActivatedPair();
        pair[1].close();
        pair[0].runPendingTasks();
        pair[1].runPendingTasks();
        assertFalse("服务端关闭后客户端连接应级联关闭", pair[0].isOpen());
        pair[0].finishAndReleaseAll();
    }

    @Test
    public void testMessageBeforeActivateIsReleased() {
        EmbeddedChannel client = new EmbeddedChannel(new RelayHandler());
        ByteBuf msg = Unpooled.wrappedBuffer("early".getBytes());
        client.writeInbound(msg);
        // ChannelInboundHandlerAdapter 不自动释放，relay 的 peer==null 分支负责 release：
        // 消息 refCnt 归零且无任何 outbound 转发
        assertNull(client.readOutbound());
        assertEquals(0, msg.refCnt());
        client.finishAndReleaseAll();
    }

    @Test
    public void testIdleEventClosesBothSides() {
        EmbeddedChannel[] pair = newActivatedPair();
        pair[0].pipeline().fireUserEventTriggered(
                IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT);
        pair[0].runPendingTasks();
        pair[1].runPendingTasks();
        assertFalse("空闲事件后客户端连接应关闭", pair[0].isOpen());
        assertFalse("空闲事件后服务端连接应级联关闭", pair[1].isOpen());
        pair[1].finishAndReleaseAll();
    }
}

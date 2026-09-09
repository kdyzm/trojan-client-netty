package com.kdyzm.trojan.client.netty.monitor;

import com.kdyzm.trojan.client.netty.router.ProxyDecision;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * 每连接流量计数器：计数累计、目标设置、快照组装
 */
public class TrafficCounterTest {

    @Test
    public void testBytesAccumulate() {
        TrafficCounter counter = new TrafficCounter(1L, "127.0.0.1:53211", 1000L);
        counter.setTarget("socks5", ProxyDecision.PROXY, "example.com", 443);
        counter.addUp(10);
        counter.addUp(20);
        counter.addDown(100);
        ConnectionSnapshot snapshot = counter.snapshot();
        assertEquals(30L, snapshot.getUpBytes());
        assertEquals(100L, snapshot.getDownBytes());
    }

    @Test
    public void testSnapshotWithoutTargetIsNull() {
        TrafficCounter counter = new TrafficCounter(2L, "127.0.0.1:53212", 1000L);
        counter.addUp(10); //即使有流量，未设目标也不可展示
        assertNull(counter.snapshot());
    }

    @Test
    public void testSnapshotFields() {
        TrafficCounter counter = new TrafficCounter(7L, "192.168.1.5:8080", 5000L);
        counter.setTarget("http", ProxyDecision.DIRECT, "baidu.com", 80);
        counter.addDown(42);
        ConnectionSnapshot snapshot = counter.snapshot();
        assertEquals(7L, snapshot.getId());
        assertEquals("192.168.1.5:8080", snapshot.getSrc());
        assertEquals("http", snapshot.getProtocol());
        assertEquals(ProxyDecision.DIRECT, snapshot.getMode());
        assertEquals("baidu.com", snapshot.getHost());
        assertEquals(80, snapshot.getPort());
        assertEquals(5000L, snapshot.getStartTimeMillis());
        assertEquals(42L, snapshot.getDownBytes());
        assertEquals(0L, snapshot.getUpBytes());
    }

    @Test
    public void testSnapshotReflectsLiveValues() {
        TrafficCounter counter = new TrafficCounter(3L, "s", 0L);
        counter.setTarget("socks5", ProxyDecision.PROXY, "h", 1);
        counter.addUp(5);
        //后续计数反映到再次快照（快照不是冻结值）
        ConnectionSnapshot first = counter.snapshot();
        assertEquals(5L, first.getUpBytes());
        counter.addUp(7);
        assertEquals(12L, counter.snapshot().getUpBytes());
    }

    @Test
    public void testNegativeBytesIgnoredAndEmptyTargetIgnored() {
        TrafficCounter counter = new TrafficCounter(4L, "s", 0L);
        counter.setTarget("", ProxyDecision.PROXY, "", 0); //空 host 忽略
        counter.setTarget("socks5", ProxyDecision.BLOCK, "blocked.com", 80);
        counter.addUp(-5); //负值忽略
        ConnectionSnapshot snapshot = counter.snapshot();
        assertEquals(0L, snapshot.getUpBytes());
        assertEquals(ProxyDecision.BLOCK, snapshot.getMode());
        assertEquals("blocked.com", snapshot.getHost());
    }
}

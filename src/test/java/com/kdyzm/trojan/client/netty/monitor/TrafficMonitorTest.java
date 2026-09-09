package com.kdyzm.trojan.client.netty.monitor;

import com.kdyzm.trojan.client.netty.router.ProxyDecision;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 连接注册表：id 分配、注册/移除、目标过滤快照
 */
public class TrafficMonitorTest {

    @Test
    public void testIdsAreUniqueAndSequential() {
        TrafficMonitor monitor = new TrafficMonitor();
        assertEquals(1L, monitor.createCounter("a").getId());
        assertEquals(2L, monitor.createCounter("b").getId());
        assertEquals(3L, monitor.createCounter("c").getId());
    }

    @Test
    public void testSnapshotFiltersUntargetedAndReturnsCopies() {
        TrafficMonitor monitor = new TrafficMonitor();
        TrafficCounter withTarget = monitor.createCounter("127.0.0.1:1000");
        withTarget.setTarget("socks5", ProxyDecision.PROXY, "example.com", 443);
        monitor.createCounter("127.0.0.1:1001"); //无目标，不应出现在快照
        TrafficCounter other = monitor.createCounter("127.0.0.1:1002");
        other.setTarget("http", ProxyDecision.DIRECT, "baidu.com", 80);
        other.addUp(9);

        List<ConnectionSnapshot> snapshots = monitor.snapshot();
        assertEquals(2, snapshots.size());
        assertTrue(snapshots.stream().noneMatch(s -> s.getHost() == null));

        //不可变拷贝：外部修改不影响内部状态
        snapshots.clear();
        assertEquals(2, monitor.snapshot().size());
    }

    @Test
    public void testRemoveCounterIsIdempotent() {
        TrafficMonitor monitor = new TrafficMonitor();
        TrafficCounter counter = monitor.createCounter("127.0.0.1:2000");
        counter.setTarget("http", ProxyDecision.PROXY, "h", 80);
        assertEquals(1, monitor.snapshot().size());
        monitor.removeCounter(counter.getId());
        assertEquals(0, monitor.snapshot().size());
        monitor.removeCounter(counter.getId()); //幂等，不抛
        assertEquals(0, monitor.snapshot().size());
    }

    @Test
    public void testSizeTracksRegistration() {
        TrafficMonitor monitor = new TrafficMonitor();
        TrafficCounter a = monitor.createCounter("a");
        monitor.createCounter("b");
        assertEquals(2, monitor.size());
        monitor.removeCounter(a.getId());
        assertEquals(1, monitor.size());
    }
}

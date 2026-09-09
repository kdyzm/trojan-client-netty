package com.kdyzm.trojan.client.netty.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 活跃连接注册表（Spring 单例）：每连接一个 TrafficCounter。
 * 线程安全：IO 线程注册/移除/打点，监控 API 线程查询快照。
 *
 * @author kdyzm
 * @date 2026-09-09
 */
@Component
@Slf4j
public class TrafficMonitor {

    private final AtomicLong idGenerator = new AtomicLong();

    private final ConcurrentHashMap<Long, TrafficCounter> counters = new ConcurrentHashMap<>();

    /**
     * 分配全局递增 id、注册并返回计数器（在客户端 channel 的 eventLoop 上调用）
     */
    public TrafficCounter createCounter(String src) {
        TrafficCounter counter = new TrafficCounter(idGenerator.incrementAndGet(), src,
                System.currentTimeMillis());
        counters.put(counter.getId(), counter);
        return counter;
    }

    /**
     * 移除连接（channelInactive 调用；幂等）
     */
    public void removeCounter(long id) {
        counters.remove(id);
    }

    /**
     * 全部已设目标连接的快照列表（新 ArrayList 拷贝，不保证顺序）
     */
    public List<ConnectionSnapshot> snapshot() {
        List<ConnectionSnapshot> result = new ArrayList<>(counters.size());
        for (TrafficCounter counter : counters.values()) {
            ConnectionSnapshot snapshot = counter.snapshot();
            if (snapshot != null) {
                result.add(snapshot);
            }
        }
        return result;
    }

    /** 当前注册连接数（测试/统计辅助） */
    public int size() {
        return counters.size();
    }
}

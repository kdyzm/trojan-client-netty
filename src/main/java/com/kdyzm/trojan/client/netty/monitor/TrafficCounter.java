package com.kdyzm.trojan.client.netty.monitor;

import com.kdyzm.trojan.client.netty.router.ProxyDecision;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 每连接流量计数器：IO 线程高频原子累加，元数据经 volatile 单引用发布（决策线程写一次）。
 * 未设置目标（握手期/认证失败的短命连接）时 snapshot() 返回 null——监控页不展示。
 *
 * @author kdyzm
 * @date 2026-09-09
 */
@Slf4j
public class TrafficCounter {

    /** 目标元数据（protocol/mode/host/port），volatile 单引用保证发布可见性 */
    private static final class Target {

        private final String protocol;

        private final ProxyDecision mode;

        private final String host;

        private final int port;

        Target(String protocol, ProxyDecision mode, String host, int port) {
            this.protocol = protocol;
            this.mode = mode;
            this.host = host;
            this.port = port;
        }
    }

    private final long id;

    private final String src;

    private final long startTimeMillis;

    private final AtomicLong upBytes = new AtomicLong();

    private final AtomicLong downBytes = new AtomicLong();

    private volatile Target target;

    public TrafficCounter(long id, String src, long startTimeMillis) {
        this.id = id;
        this.src = src;
        this.startTimeMillis = startTimeMillis;
    }

    public long getId() {
        return id;
    }

    /**
     * 设置连接目标（协议/模式/主机/端口）；host 为 null 或空串时忽略（保持未设目标）
     */
    public void setTarget(String protocol, ProxyDecision mode, String host, int port) {
        if (host == null || host.isEmpty()) {
            return;
        }
        this.target = new Target(protocol, mode, host, port);
    }

    /** 累计上行字节（浏览器发出方向）；负数忽略 */
    public void addUp(long bytes) {
        if (bytes > 0) {
            upBytes.addAndGet(bytes);
        }
    }

    /** 累计下行字节（返回浏览器方向）；负数忽略 */
    public void addDown(long bytes) {
        if (bytes > 0) {
            downBytes.addAndGet(bytes);
        }
    }

    /**
     * 组装当前快照；未设置目标时返回 null
     */
    public ConnectionSnapshot snapshot() {
        Target current = target;
        if (current == null) {
            return null;
        }
        return new ConnectionSnapshot(id, src, current.protocol, current.mode,
                current.host, current.port, startTimeMillis,
                upBytes.get(), downBytes.get());
    }
}

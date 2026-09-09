package com.kdyzm.trojan.client.netty.monitor;

import com.kdyzm.trojan.client.netty.router.ProxyDecision;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 单条活跃连接的不可变快照（监控 API 返回的最小单位）
 */
@Data
@AllArgsConstructor
public class ConnectionSnapshot {

    /** 服务端分配的全局唯一 id（前端差分的稳定 key） */
    private final long id;

    /** 来源地址 host:port */
    private final String src;

    /** 协议：socks5 / http */
    private final String protocol;

    /** 模式：PROXY / DIRECT / BLOCK */
    private final ProxyDecision mode;

    /** 目标地址（域名或 IP） */
    private final String host;

    /** 目标端口 */
    private final int port;

    /** 建连时间（epoch millis） */
    private final long startTimeMillis;

    /** 上行累计字节（浏览器发出） */
    private final long upBytes;

    /** 下行累计字节（返回浏览器） */
    private final long downBytes;
}

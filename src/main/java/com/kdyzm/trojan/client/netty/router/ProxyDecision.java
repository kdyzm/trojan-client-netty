package com.kdyzm.trojan.client.netty.router;

/**
 * 代理决策结果：走 trojan 代理 / 直连 / 拒绝
 */
public enum ProxyDecision {
    PROXY,
    DIRECT,
    BLOCK
}

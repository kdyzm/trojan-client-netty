package com.kdyzm.trojan.client.netty.router;

import com.kdyzm.trojan.client.netty.enums.ProxyModelEnum;
import com.kdyzm.trojan.client.netty.models.PacModel;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 代理决策器：socks5 与 http 两条路径共用的名单匹配 + 模式决策。
 * 名单条目按域名长度降序，匹配采用最长后缀优先（dstHost 小写后 endsWith）。
 * 决策语义与 v6 历史行为一致：
 * - 命中 block(-1)：任何模式拒绝
 * - direct 模式：除 block 外一律直连（名单 proxy 条目失效）
 * - global 模式：名单 direct 直连，其余（proxy 或未命中）代理
 * - pac 模式：名单按自身值，未命中直连
 *
 * @author kdyzm
 * @date 2026-09-08
 */
@Slf4j
public class ProxyRouter {

    /** 按域名长度降序的名单，保证最长后缀优先匹配 */
    private final List<PacModel> sortedPacList = new ArrayList<>();

    private final ProxyModelEnum proxyMode;

    public ProxyRouter(Map<String, PacModel> pacMap, String proxyMode) {
        if (pacMap != null) {
            sortedPacList.addAll(pacMap.values());
            sortedPacList.sort(Comparator.comparingInt((PacModel m) -> m.getDomainName().length()).reversed());
        }
        this.proxyMode = ProxyModelEnum.get(proxyMode);
    }

    /**
     * 决策目标地址的处理方式
     *
     * @param dstHost 目标域名或 IP（socks5 CONNECT 的 dstAddr / http Host 头解析出的 host）
     */
    public ProxyDecision decide(String dstHost) {
        String host = dstHost == null ? "" : dstHost.toLowerCase(Locale.ROOT);
        PacModel hit = null;
        for (PacModel model : sortedPacList) {
            if (host.endsWith(model.getDomainName())) {
                hit = model;
                break;
            }
        }
        if (hit != null && hit.isBlock()) {
            return ProxyDecision.BLOCK;
        }
        switch (proxyMode) {
            case DIRECT:
                return ProxyDecision.DIRECT;
            case PAC:
                if (hit == null) {
                    return ProxyDecision.DIRECT;
                }
                return hit.isProxy() ? ProxyDecision.PROXY : ProxyDecision.DIRECT;
            case GLOBAL:
            default:
                // 名单内 direct 直连，名单内 proxy 或未命中一律走代理
                return hit != null && hit.isDirect() ? ProxyDecision.DIRECT : ProxyDecision.PROXY;
        }
    }
}

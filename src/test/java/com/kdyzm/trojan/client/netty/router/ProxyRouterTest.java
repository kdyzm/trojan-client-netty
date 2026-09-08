package com.kdyzm.trojan.client.netty.router;

import com.kdyzm.trojan.client.netty.models.PacModel;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * 决策矩阵测试：GLOBAL/PAC/DIRECT × 名单命中类别 × 最长后缀匹配
 */
public class ProxyRouterTest {

    private Map<String, PacModel> pacMap;

    private PacModel pac(String domain, int mode) {
        PacModel model = new PacModel();
        model.setDomainName(domain);
        model.setProxyMode(mode);
        return model;
    }

    @Before
    public void setUp() {
        pacMap = new HashMap<>();
        pacMap.put("google.com", pac("google.com", 0));      // DIRECT
        pacMap.put("baidu.com", pac("baidu.com", 1));        // PROXY
        pacMap.put("blocked.com", pac("blocked.com", -1));   // BLOCK
        // 同时存在宽条目与更具体的窄条目，验证最长后缀优先
        pacMap.put("example.com", pac("example.com", 1));
        pacMap.put("www.example.com", pac("www.example.com", -1));
    }

    private void assertDecide(String mode, String host, ProxyDecision expected) {
        assertEquals(expected, new ProxyRouter(pacMap, mode).decide(host));
    }

    @Test
    public void testGlobalMode() {
        assertDecide("global", "baidu.com", ProxyDecision.PROXY);
        assertDecide("global", "google.com", ProxyDecision.DIRECT);
        assertDecide("global", "blocked.com", ProxyDecision.BLOCK);
        assertDecide("global", "unknown.org", ProxyDecision.PROXY);   // global 默认代理
    }

    @Test
    public void testPacMode() {
        assertDecide("pac", "baidu.com", ProxyDecision.PROXY);
        assertDecide("pac", "google.com", ProxyDecision.DIRECT);
        assertDecide("pac", "blocked.com", ProxyDecision.BLOCK);
        assertDecide("pac", "unknown.org", ProxyDecision.DIRECT);     // pac 默认直连
    }

    @Test
    public void testDirectMode() {
        assertDecide("direct", "baidu.com", ProxyDecision.DIRECT);    // 名单 proxy 在 direct 模式失效（原语义）
        assertDecide("direct", "google.com", ProxyDecision.DIRECT);
        assertDecide("direct", "blocked.com", ProxyDecision.BLOCK);   // 名单 block 在任何模式生效
        assertDecide("direct", "unknown.org", ProxyDecision.DIRECT);
    }

    @Test
    public void testSubdomainSuffixMatch() {
        assertDecide("global", "sub.baidu.com", ProxyDecision.PROXY); // endsWith 命中子域
        assertDecide("pac", "a.b.c.blocked.com", ProxyDecision.BLOCK);
    }

    @Test
    public void testLongestSuffixWins() {
        // www.example.com 应命中窄条目(-1)而非宽条目(1)
        assertDecide("global", "www.example.com", ProxyDecision.BLOCK);
        assertDecide("global", "deep.www.example.com", ProxyDecision.BLOCK);
        // example.com 本身命中宽条目
        assertDecide("global", "example.com", ProxyDecision.PROXY);
    }

    @Test
    public void testCaseInsensitive() {
        assertDecide("global", "BAIDU.COM", ProxyDecision.PROXY);
        assertDecide("global", "Blocked.Com", ProxyDecision.BLOCK);
    }
}

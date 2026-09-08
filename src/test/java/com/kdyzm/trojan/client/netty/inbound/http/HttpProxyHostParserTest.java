package com.kdyzm.trojan.client.netty.inbound.http;

import com.kdyzm.trojan.client.netty.models.HostPort;
import org.junit.Test;

import static com.kdyzm.trojan.client.netty.inbound.http.HttpProxyInboundHandler.parseHostHeader;
import static org.junit.Assert.assertEquals;

/**
 * Host 头解析：IPv6 字面量 [::1]:443、域名、host:port、非法端口
 */
public class HttpProxyHostParserTest {

    private void assertHostPort(String header, String host, int port) {
        HostPort hp = parseHostHeader(header);
        assertEquals(host, hp.getHost());
        assertEquals(port, hp.getPort());
    }

    @Test
    public void testPlainDomain() {
        assertHostPort("example.com", "example.com", -1);
    }

    @Test
    public void testDomainWithPort() {
        assertHostPort("example.com:8080", "example.com", 8080);
    }

    @Test
    public void testIpv4WithPort() {
        assertHostPort("1.2.3.4:80", "1.2.3.4", 80);
    }

    @Test
    public void testIpv6LiteralWithPort() {
        // 修复前 split(":") 会把 [::1]:443 拆碎并抛 NumberFormatException
        assertHostPort("[::1]:443", "::1", 443);
    }

    @Test
    public void testIpv6LiteralWithoutPort() {
        assertHostPort("[2001:db8::1]", "2001:db8::1", -1);
    }

    @Test
    public void testWhitespaceTrimmed() {
        assertHostPort("  example.com:80  ", "example.com", 80);
    }

    @Test
    public void testInvalidPortFallsBackToMinusOne() {
        assertHostPort("example.com:abc", "example.com", -1);
        assertHostPort("example.com:0", "example.com", -1);
        assertHostPort("example.com:70000", "example.com", -1);
    }

    @Test
    public void testNullAndEmpty() {
        assertHostPort(null, "", -1);
        assertHostPort("", "", -1);
    }
}

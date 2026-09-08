package com.kdyzm.trojan.client.netty.inbound.http;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * http 代理 Basic 认证测试：407 响应、凭据校验、通过后自移除并放行
 */
public class HttpAuthInboundHandlerTest {

    private Map<String, String> users;

    private HttpRequest request() {
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "http://example.com/");
        req.headers().set(HttpHeaderNames.HOST, "example.com");
        return req;
    }

    private String basicHeader(String username, String password) {
        String raw = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Before
    public void setUp() {
        users = new HashMap<>();
        users.put("alice", "secret123");
    }

    @Test
    public void testNoHeaderReturns407AndCloses() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpAuthInboundHandler(users));
        channel.writeInbound(request());
        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED, response.status());
        assertEquals("Basic realm=\"proxy\"", response.headers().get(HttpHeaderNames.PROXY_AUTHENTICATE));
        assertFalse("认证失败后应关闭连接", channel.isOpen());
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void testWrongCredentialsReturns407() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpAuthInboundHandler(users));
        HttpRequest req = request();
        req.headers().set(HttpHeaderNames.PROXY_AUTHORIZATION, basicHeader("alice", "wrong"));
        channel.writeInbound(req);
        FullHttpResponse response = channel.readOutbound();
        assertEquals(HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED, response.status());
        assertFalse(channel.isOpen());
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void testValidCredentialsPassesThroughAndRemovesSelf() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpAuthInboundHandler(users));
        HttpRequest req = request();
        req.headers().set(HttpHeaderNames.PROXY_AUTHORIZATION, basicHeader("alice", "secret123"));
        channel.writeInbound(req);
        //通过：无 407 出站；handler 自移除；消息放行到链尾（无后续 handler 时消息被释放）
        assertNull("认证通过不应产生 407 响应", channel.readOutbound());
        assertNull("认证通过后 handler 应自移除", channel.pipeline().get(HttpAuthInboundHandler.class));
        assertTrue(channel.isOpen());
        channel.finishAndReleaseAll();
    }

    @Test
    public void testSchemeCaseInsensitiveAndTrimmed() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpAuthInboundHandler(users));
        HttpRequest req = request();
        String header = basicHeader(" alice ", " secret123 ");
        req.headers().set(HttpHeaderNames.PROXY_AUTHORIZATION, "basic " + header.substring("Basic ".length()));
        channel.writeInbound(req);
        assertNull(channel.readOutbound());
        assertNull(channel.pipeline().get(HttpAuthInboundHandler.class));
        channel.finishAndReleaseAll();
    }
}

package com.kdyzm.trojan.client.netty.monitor;

import com.kdyzm.trojan.client.netty.router.ProxyDecision;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 监控 HTTP 服务：页面与 API 路由
 */
public class MonitorHttpHandlerTest {

    private TrafficMonitor monitorWithOneConnection() {
        TrafficMonitor monitor = new TrafficMonitor();
        TrafficCounter counter = monitor.createCounter("127.0.0.1:9999");
        counter.setTarget("socks5", ProxyDecision.PROXY, "example.com", 443);
        counter.addUp(100);
        return monitor;
    }

    private EmbeddedChannel newChannel(TrafficMonitor monitor) {
        //注：不套 HttpServerCodec——其 HttpResponseEncoder 会把出站 FullHttpResponse 编成 ByteBuf，
        //EmbeddedChannel 读出的将是原始编码字节而非 FullHttpResponse 对象。
        //HttpRequestDecoder 对非 ByteBuf 输入本就直接透传，故 handler 收到的仍是完整 HttpRequest，
        //等价于真实 pipeline 解码后的输入；编码侧由 MonitorServerInitializer 集成时验证。
        return new EmbeddedChannel(new MonitorHttpHandler(monitor));
    }

    private FullHttpResponse request(EmbeddedChannel channel, HttpMethod method, String uri) {
        channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri));
        FullHttpResponse response = channel.readOutbound();
        channel.runPendingTasks(); //CLOSE listener
        return response;
    }

    @Test
    public void testIndexServesHtml() {
        EmbeddedChannel channel = newChannel(monitorWithOneConnection());
        FullHttpResponse response = request(channel, HttpMethod.GET, "/");
        assertEquals(HttpResponseStatus.OK, response.status());
        assertTrue(response.headers().get(HttpHeaderNames.CONTENT_TYPE).contains("text/html"));
        String body = response.content().toString(CharsetUtil.UTF_8);
        assertTrue(body.contains("<html"));
        assertTrue(body.contains("/api/connections"));
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void testApiReturnsConnectionsJson() {
        EmbeddedChannel channel = newChannel(monitorWithOneConnection());
        FullHttpResponse response = request(channel, HttpMethod.GET, "/api/connections");
        assertEquals(HttpResponseStatus.OK, response.status());
        String body = response.content().toString(CharsetUtil.UTF_8);
        assertTrue(body.contains("\"host\":\"example.com\""));
        assertTrue(body.contains("\"upBytes\":100"));
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void testUnknownPathReturns404() {
        EmbeddedChannel channel = newChannel(monitorWithOneConnection());
        FullHttpResponse response = request(channel, HttpMethod.GET, "/other");
        assertEquals(HttpResponseStatus.NOT_FOUND, response.status());
        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void testNonGetReturns405() {
        EmbeddedChannel channel = newChannel(monitorWithOneConnection());
        FullHttpResponse response = request(channel, HttpMethod.POST, "/api/connections");
        assertEquals(HttpResponseStatus.METHOD_NOT_ALLOWED, response.status());
        response.release();
        channel.finishAndReleaseAll();
    }
}

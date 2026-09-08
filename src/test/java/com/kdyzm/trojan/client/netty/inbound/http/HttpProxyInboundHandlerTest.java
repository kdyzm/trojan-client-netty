package com.kdyzm.trojan.client.netty.inbound.http;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * HTTP 代理失败应答工具测试
 */
public class HttpProxyInboundHandlerTest {

    @Test
    public void testBadGatewayResponse() {
        FullHttpResponse response = HttpProxyInboundHandler.badGatewayResponse(HttpVersion.HTTP_1_1);
        assertEquals(HttpResponseStatus.BAD_GATEWAY, response.status());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        response.release();
    }

    @Test
    public void testBadGatewayResponseHttp10() {
        FullHttpResponse response = HttpProxyInboundHandler.badGatewayResponse(HttpVersion.HTTP_1_0);
        assertEquals(HttpVersion.HTTP_1_0, response.protocolVersion());
        response.release();
    }
}

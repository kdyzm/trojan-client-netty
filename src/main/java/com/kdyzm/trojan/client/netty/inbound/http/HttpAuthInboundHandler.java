package com.kdyzm.trojan.client.netty.inbound.http;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * http 代理端口的 Basic 认证（Proxy-Authorization 头，RFC 7235）。
 * 凭据复用 users.properties（与 socks5 认证同一用户表）。
 * 认证通过：自移除并放行原请求（同一消息对象继续流向代理决策 handler）；
 * 认证失败：回 407 + Proxy-Authenticate 后关闭；非 HttpRequest 帧（请求体）一律放行不消费。
 *
 * @author kdyzm
 * @date 2026-09-08
 */
@Slf4j
public class HttpAuthInboundHandler extends ChannelInboundHandlerAdapter {

    private final Map<String, String> users;

    public HttpAuthInboundHandler(Map<String, String> users) {
        this.users = users;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof HttpRequest)) {
            //请求体等非请求帧：不拦截，继续向后传递
            ctx.fireChannelRead(msg);
            return;
        }
        HttpRequest request = (HttpRequest) msg;
        if (authenticate(request)) {
            //认证通过：移除自身，放行请求继续流向代理决策 handler
            ctx.pipeline().remove(this);
            ctx.fireChannelRead(msg);
            return;
        }
        log.warn("http 代理认证失败: {}", request.uri());
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED);
        response.headers().set(HttpHeaderNames.PROXY_AUTHENTICATE, "Basic realm=\"proxy\"");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private boolean authenticate(HttpRequest request) {
        String authorization = request.headers().get(HttpHeaderNames.PROXY_AUTHORIZATION);
        if (authorization == null) {
            return false;
        }
        String[] parts = authorization.trim().split("\\s+", 2);
        if (parts.length != 2 || !"Basic".equalsIgnoreCase(parts[0])) {
            return false;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon < 0) {
                return false;
            }
            String username = decoded.substring(0, colon);
            String password = decoded.substring(colon + 1);
            String stored = users.get(username.trim());
            return stored != null && stored.equals(password.trim());
        } catch (IllegalArgumentException e) {
            //非法的 Base64 编码
            return false;
        }
    }
}

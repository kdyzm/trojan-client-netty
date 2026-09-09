package com.kdyzm.trojan.client.netty.monitor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 监控 HTTP 路由：GET / 返回页面；GET /api/connections 返回连接快照 JSON。
 * 所有响应后关闭连接（无 keep-alive 状态，简化帧处理）。
 *
 * @author kdyzm
 * @date 2026-09-09
 */
@Slf4j
public class MonitorHttpHandler extends ChannelInboundHandlerAdapter {

    /** classpath 页面资源（首次读取后缓存；读取失败则用错误页并记录） */
    private static final String MONITOR_HTML_PATH = "/monitor.html";

    private static byte[] cachedHtml;

    private static final byte[] ERROR_HTML = ("<html><body><h1>monitor.html 加载失败</h1>"
            + "请确认 jar 内包含该资源</body></html>").getBytes(StandardCharsets.UTF_8);

    private final TrafficMonitor monitor;

    public MonitorHttpHandler(TrafficMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof HttpRequest)) {
            //无下游 handler：请求体帧须显式释放
            ReferenceCountUtil.release(msg);
            return;
        }
        HttpRequest request = (HttpRequest) msg;
        HttpMethod method = request.method();
        String uri = request.uri();
        if (!method.equals(HttpMethod.GET)) {
            respond(ctx, request, HttpResponseStatus.METHOD_NOT_ALLOWED, "method not allowed".getBytes(StandardCharsets.UTF_8), "text/plain; charset=UTF-8");
            return;
        }
        if ("/".equals(uri)) {
            respond(ctx, request, HttpResponseStatus.OK, loadHtml(), "text/html; charset=UTF-8");
            return;
        }
        if ("/api/connections".equals(uri)) {
            String json = MonitorJsonWriter.writeConnections(monitor.snapshot());
            respond(ctx, request, HttpResponseStatus.OK, json.getBytes(StandardCharsets.UTF_8), "application/json; charset=UTF-8");
            return;
        }
        respond(ctx, request, HttpResponseStatus.NOT_FOUND, "not found".getBytes(StandardCharsets.UTF_8), "text/plain; charset=UTF-8");
    }

    private static byte[] loadHtml() {
        byte[] cached = cachedHtml;
        if (cached == null) {
            synchronized (MonitorHttpHandler.class) {
                cached = cachedHtml;
                if (cached == null) {
                    try (InputStream in = MonitorHttpHandler.class.getResourceAsStream(MONITOR_HTML_PATH)) {
                        if (in == null) {
                            log.error("classpath 资源 {} 不存在", MONITOR_HTML_PATH);
                            cachedHtml = ERROR_HTML;
                            return ERROR_HTML;
                        }
                        ByteBuf buf = Unpooled.buffer();
                        byte[] chunk = new byte[4096];
                        int read;
                        while ((read = in.read(chunk)) != -1) {
                            buf.writeBytes(chunk, 0, read);
                        }
                        byte[] html = new byte[buf.readableBytes()];
                        buf.readBytes(html);
                        buf.release();
                        cachedHtml = html;
                        cached = html;
                    } catch (IOException e) {
                        log.error("读取 {} 失败", MONITOR_HTML_PATH, e);
                        cachedHtml = ERROR_HTML;
                        return ERROR_HTML;
                    }
                }
            }
        }
        return cached;
    }

    private void respond(ChannelHandlerContext ctx, HttpRequest request,
                         HttpResponseStatus status, byte[] body, String contentType) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.wrappedBuffer(body));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.length);
        //统一关闭连接，避免 keep-alive 残留帧处理
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}

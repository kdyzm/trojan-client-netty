package com.kdyzm.trojan.client.netty.inbound;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 黑名单拦截提示页：返回 blacklist.html 内容后按 keep-alive 决定是否关闭。
 * 页面字节首次读取后静态缓存，避免每请求重开文件（原 DefaultFileRegion 在文件关闭
 * 与异步 flush 之间存在生命周期竞态）。
 *
 * @author kdyzm
 * @date 2021/4/26
 */
@Slf4j
public class BlackListInboundHandler extends ChannelInboundHandlerAdapter {

    private static final String BLACKLIST_HTML = "blacklist.html";

    /** 页面字节缓存（首次读取后不再触碰文件系统） */
    private static volatile byte[] cachedHtml;

    private static byte[] loadHtml() {
        byte[] cached = cachedHtml;
        if (cached == null) {
            synchronized (BlackListInboundHandler.class) {
                cached = cachedHtml;
                if (cached == null) {
                    try {
                        cached = Files.readAllBytes(Paths.get(BLACKLIST_HTML));
                        cachedHtml = cached;
                    } catch (IOException e) {
                        //读取失败则回退为最小提示页，避免黑名单拦截路径崩溃
                        log.error("读取 {} 失败，使用内置提示页", BLACKLIST_HTML, e);
                        cached = "<html><body><h1>Access Denied</h1></body></html>"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
            }
        }
        return cached;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof HttpRequest)) {
            log.info("非 http 请求，直接关闭channel");
            //socks5 腿上的 TLS/原始字节等：先释放再关闭，避免引用泄漏
            ReferenceCountUtil.release(msg);
            ctx.channel().close();
            return;
        }
        HttpRequest request = (HttpRequest) msg;
        log.info("请求方式：{}", request.method().name());
        log.info("请求uri：{}", request.uri());
        if ("/favicon.ico".equalsIgnoreCase(request.uri())) {
            log.info("不处理 /favicon.ico 请求");
            ctx.channel().close();
            return;
        }
        byte[] html = loadHtml();
        HttpResponse response = new DefaultHttpResponse(request.protocolVersion(), HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, html.length);
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        ctx.write(response);
        ByteBuf content = Unpooled.wrappedBuffer(html);
        ctx.write(content);
        ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }
}

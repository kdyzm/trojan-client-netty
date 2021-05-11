package com.kdyzm.trojan.client.netty.inbound;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.util.CharsetUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URL;

/**
 * @author kdyzm
 * @date 2021/4/26
 */
@Slf4j
@AllArgsConstructor
public class BlackListInboundHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof HttpRequest)) {
            log.info("非http请求，直接关闭channel");
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
        if(!("".equalsIgnoreCase(request.uri())||"/".equalsIgnoreCase(request.uri()))){
            log.info("非主页请求，直接关闭channel");
            ctx.channel().close();
            return;
        }
        URL resource = this.getClass().getClassLoader().getResource("blacklist.html");
        assert resource != null;
        RandomAccessFile file = new RandomAccessFile(new File(resource.getFile()), "r");
        HttpResponse response = new DefaultHttpResponse(request.protocolVersion(), HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, file.length());
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        ctx.write(response);
        ctx.write(new DefaultFileRegion(file.getChannel(), 0, file.length()));
        ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
        file.close();
        ctx.flush();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().close();
    }
}

package com.kdyzm.socks5.netty.inbound;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author kdyzm
 * @date 2021-04-23
 */
@Slf4j
public class Client2DestInboundHandler extends ChannelInboundHandlerAdapter {

    private final ChannelFuture dstChannelFuture;

    public Client2DestInboundHandler(ChannelFuture dstChannelFuture) {
        this.dstChannelFuture = dstChannelFuture;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        log.debug("转发客户端的请求到代理服务器");
        dstChannelFuture.channel().writeAndFlush(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.trace("客户端与代理服务器的连接已经断开，即将断开代理服务器和目标服务器的连接");
        dstChannelFuture.channel().close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Client2DestInboundHandler exception", cause);
    }
}

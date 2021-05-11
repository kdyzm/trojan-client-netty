package com.kdyzm.trojan.client.netty.inbound;

import com.kdyzm.trojan.client.netty.util.SocksServerUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
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
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        log.debug("转发客户端的请求到代理服务器");
        if (dstChannelFuture.channel().isActive()) {
            dstChannelFuture.channel().writeAndFlush(msg);
        } else {
            log.info("释放内存");
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.trace("客户端与代理服务器的连接已经断开，即将断开代理服务器和目标服务器的连接");
        if (dstChannelFuture.channel().isActive()) {
            SocksServerUtils.closeOnFlush(ctx.channel());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Client2DestInboundHandler exception", cause);
        ctx.close();
    }
}

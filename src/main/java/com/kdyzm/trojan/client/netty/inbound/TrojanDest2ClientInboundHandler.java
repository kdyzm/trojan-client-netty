package com.kdyzm.trojan.client.netty.inbound;

import com.kdyzm.trojan.client.netty.util.SocksServerUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * @author kdyzm
 * @date 2021/4/29
 */
@Slf4j
public class TrojanDest2ClientInboundHandler extends ChannelInboundHandlerAdapter {


    private final ChannelHandlerContext clientChannelHandlerContext;

    public TrojanDest2ClientInboundHandler(ChannelHandlerContext clientChannelHandlerContext) {
        this.clientChannelHandlerContext = clientChannelHandlerContext;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        log.trace("开始写回客户端");
        if (clientChannelHandlerContext.channel().isActive()) {
            clientChannelHandlerContext.writeAndFlush(msg);
        } else {
            log.info("释放内存");
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.trace("代理服务器和目标服务器的连接已经断开，即将断开客户端和代理服务器的连接");
        if (clientChannelHandlerContext.channel().isActive()) {
            SocksServerUtils.closeOnFlush(clientChannelHandlerContext.channel());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.info("TrojanDest2ClientInboundHandler exception", cause);
        ctx.close();
    }
}

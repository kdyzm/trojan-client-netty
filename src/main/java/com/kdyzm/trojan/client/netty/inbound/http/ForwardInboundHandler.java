package com.kdyzm.trojan.client.netty.inbound.http;

import com.kdyzm.trojan.client.netty.util.SocksServerUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP 直连路径的双向透传 handler。
 * 持有对端 channel：本侧读到数据转发给对端；任一侧断开时级联关闭另一侧，
 * 避免 http 直连连接泄漏（分析报告 P1-②）。
 *
 * @author kdyzm
 * @date 2021/5/14
 */
@Slf4j
public class ForwardInboundHandler extends ChannelInboundHandlerAdapter {

    private Channel outChannel;

    public ForwardInboundHandler(Channel outChannel) {
        this.outChannel = outChannel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (outChannel.isActive()) {
            outChannel.write(msg);
        } else {
            log.info("对端连接已关闭，释放数据");
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        outChannel.flush();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.trace("本侧连接已断开，即将关闭对端连接");
        if (outChannel.isActive()) {
            SocksServerUtils.closeOnFlush(outChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("ForwardInboundHandler exception", cause);
        ctx.close();
    }
}

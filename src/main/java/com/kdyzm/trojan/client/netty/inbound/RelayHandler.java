package com.kdyzm.trojan.client.netty.inbound;

import com.kdyzm.trojan.client.netty.util.SocksServerUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一双向透传 handler（Phase 2 收敛五件套）。
 * 两种装配方式：
 * - 客户端侧：先无参入链，连接建立成功后由客户端 eventLoop 调用 activate(peer)
 * - 出站侧：构造时直接传入对端 channel（客户端 channel 引用）
 * 行为：数据转发对端；任一侧断开级联关闭对端；空闲事件（读写双向静默）双向回收；
 * 未激活（peer 为空）期间收到的消息直接释放。
 *
 * @author kdyzm
 * @date 2026-09-08
 */
@Slf4j
public class RelayHandler extends ChannelInboundHandlerAdapter {

    private Channel peer;

    public RelayHandler() {
    }

    public RelayHandler(Channel peer) {
        this.peer = peer;
    }

    /**
     * 激活转发目标；须在持有本 handler 的 channel 的 eventLoop 线程调用（幂等）
     */
    public void activate(Channel peer) {
        this.peer = peer;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (peer != null && peer.isActive()) {
            peer.writeAndFlush(msg);
        } else {
            log.info("对端连接未就绪或已关闭，释放数据");
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.trace("本侧连接已断开，级联关闭对端连接");
        if (peer != null && peer.isActive()) {
            SocksServerUtils.closeOnFlush(peer);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            log.info("空闲超时，回收空闲连接");
            if (peer != null && peer.isActive()) {
                SocksServerUtils.closeOnFlush(peer);
            }
            ctx.close();
            return;
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("RelayHandler exception", cause);
        ctx.close();
    }
}

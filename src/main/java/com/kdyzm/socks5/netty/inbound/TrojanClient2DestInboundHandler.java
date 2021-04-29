package com.kdyzm.socks5.netty.inbound;

import com.kdyzm.socks5.netty.encoder.TrojanRequestEncoder;
import com.kdyzm.socks5.netty.models.TrojanRequest;
import com.kdyzm.socks5.netty.models.TrojanWrapperRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author kdyzm
 * @date 2021/4/29
 */
@Slf4j
@RequiredArgsConstructor
public class TrojanClient2DestInboundHandler extends ChannelInboundHandlerAdapter {

    private final ChannelFuture dstChannelFuture;
    private final String dstAddr;
    private final int dstPort;
    private final ChannelHandlerContext dstContext;
    private final Socks5AddressType socks5AddressType;
    private final String trojanPassword;

    enum State {
        /**
         *
         */
        INIT,
        SUCCESS
    }

    private State state = State.INIT;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        log.debug("转发客户端的请求到代理服务器");
        if (state == State.INIT) {
            dstChannelFuture.channel().writeAndFlush(getTrojanWrapperRequest(msg));
            state = State.SUCCESS;
        } else {
            dstChannelFuture.channel().writeAndFlush(msg);
        }
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

    private TrojanWrapperRequest getTrojanWrapperRequest(Object msg) {
        TrojanWrapperRequest trojanWrapperRequest = new TrojanWrapperRequest();
        TrojanRequest trojanRequest = new TrojanRequest();
        trojanRequest.setAtyp(socks5AddressType.byteValue());
        trojanRequest.setCmd(0X01);
        trojanRequest.setDstPort(dstPort);
        trojanRequest.setDstAddr(dstAddr);
        trojanWrapperRequest.setPassword(trojanPassword);
        trojanWrapperRequest.setPayload(msg);
        return trojanWrapperRequest;
    }
}

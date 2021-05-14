package com.kdyzm.trojan.client.netty.inbound.http;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * @author kdyzm
 * @date 2021/5/14
 */
public class ForwardInboundHandler extends ChannelInboundHandlerAdapter {

    private Channel outChannel;

    public ForwardInboundHandler(Channel outChannel) {
        this.outChannel = outChannel;
    }


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        outChannel.write(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        outChannel.flush();
    }
}
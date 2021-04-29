package com.kdyzm.socks5.netty.inbound;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author kdyzm
 * @date 2021/4/28
 */
@Slf4j
@AllArgsConstructor
public class TrojanResponseInboundHandler extends ChannelInboundHandlerAdapter {

    private ChannelHandlerContext clientContext;

    private Socks5AddressType bndAddrType;


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.pipeline().addLast(new Dest2ClientInboundHandler(clientContext));
        log.debug("trojan协议响应完毕");
        DefaultSocks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, bndAddrType);
        clientContext.writeAndFlush(commandResponse).addListener(complete -> ctx.pipeline().remove(TrojanResponseInboundHandler.class));

    }
}

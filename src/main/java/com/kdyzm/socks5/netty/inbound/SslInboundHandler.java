package com.kdyzm.socks5.netty.inbound;

import com.kdyzm.socks5.netty.encoder.TrojanRequestEncoder;
import com.kdyzm.socks5.netty.models.TrojanRequest;
import com.kdyzm.socks5.netty.models.TrojanWrapperRequest;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author kdyzm
 * @date 2021/4/28
 */
@Slf4j
@AllArgsConstructor
public class SslInboundHandler extends ChannelInboundHandlerAdapter {

    private String dstAddr;

    private Integer dstPort;

    private ChannelHandlerContext clientContext;

    private Socks5AddressType bndAddrType;
    
    private String password;
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        log.debug("ssl inbound handler");
//        ctx.pipeline().addLast(new Dest2ClientInboundHandler(clientContext));
        ctx.writeAndFlush(getTrojanWrapperRequest());
        ctx.pipeline().addLast(new TrojanResponseInboundHandler(clientContext,bndAddrType));
        ctx.pipeline().remove(this);
    }

    private TrojanWrapperRequest getTrojanWrapperRequest() {
        TrojanWrapperRequest trojanWrapperRequest = new TrojanWrapperRequest();
        TrojanRequest trojanRequest = new TrojanRequest();
        trojanRequest.setAtyp(0X03);
        trojanRequest.setCmd(0X01);
        trojanRequest.setDstPort(dstPort);
        trojanRequest.setDstAddr(dstAddr);
        trojanWrapperRequest.setPassword(password);
        return trojanWrapperRequest;
    }
}

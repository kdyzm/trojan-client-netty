package com.kdyzm.trojan.client.netty.encoder;

import com.kdyzm.trojan.client.netty.constants.TrojanAddressType;
import com.kdyzm.trojan.client.netty.models.TrojanRequest;
import com.kdyzm.trojan.client.netty.models.TrojanWrapperRequest;
import com.kdyzm.trojan.client.netty.util.Sha256Util;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * @author kdyzm
 * @date 2021/4/28
 */
@Slf4j
public class TrojanRequestEncoder extends MessageToByteEncoder<TrojanWrapperRequest> {

    /**
     * TLS连接成功后，尝试进行trojan协议握手
     * <pre>
     * +-----------------------+---------+----------------+---------+----------+
     * | hex(SHA224(password)) |  CRLF   | Trojan Request |  CRLF   | Payload  |
     * +-----------------------+---------+----------------+---------+----------+
     * |          56           | X'0D0A' |    Variable    | X'0D0A' | Variable |
     * +-----------------------+---------+----------------+---------+----------+
     * where Trojan Request is a SOCKS5-like request:
     *
     * +-----+------+----------+----------+
     * | CMD | ATYP | DST.ADDR | DST.PORT |
     * +-----+------+----------+----------+
     * |  1  |  1   | Variable |    2     |
     * +-----+------+----------+----------+
     *
     * where:
     *
     *     o  CMD
     *         o  CONNECT X'01'
     *         o  UDP ASSOCIATE X'03'
     *     o  ATYP address type of following address
     *         o  IP V4 address: X'01'
     *         o  DOMAINNAME: X'03'
     *         o  IP V6 address: X'04'
     *     o  DST.ADDR desired destination address
     *     o  DST.PORT desired destination port in network octet order
     * </pre>
     */

    enum State {
        /**
         *
         */
        INIT,
        SUCCESS
    }

    private State state;

    public TrojanRequestEncoder() {
        this.state = State.INIT;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, TrojanWrapperRequest msg, ByteBuf out) throws Exception {
        log.debug("请求代理服务器发起trojan协议握手");
        switch (state) {
            case INIT:
                log.debug("trojan协议初次握手");
                String password = Sha256Util.encryptThisString(msg.getPassword());
                TrojanRequest trojanRequest = msg.getTrojanRequest();
                out.writeCharSequence(password, StandardCharsets.UTF_8);
                out.writeByte(0X0D);
                out.writeByte(0X0A);
                out.writeByte(trojanRequest.getCmd());
                out.writeByte(trojanRequest.getAtyp());
                encodeAddress(trojanRequest.getAtyp(), out, trojanRequest.getDstAddr());
                out.writeShort(trojanRequest.getDstPort());
                out.writeByte(0X0D);
                out.writeByte(0X0A);
                out.writeBytes((ByteBuf) msg.getPayload());
                state = State.SUCCESS;
                break;
            case SUCCESS:
                log.debug("转发trojan数据");
                out.writeBytes((ByteBuf) msg.getPayload());
                break;
            default:
                log.debug("未知的状态数据");
        }

    }

    /**
     * 加密密码
     *
     * @param addressType
     * @param out
     * @param dstAddr
     */
    private void encodeAddress(int addressType, ByteBuf out, String dstAddr) {
        if (addressType == TrojanAddressType.IPV4) {
            String[] split = dstAddr.split("\\.");
            for (String item : split) {
                int b = Integer.parseInt(item);
                out.writeByte(b);
            }
        } else if (addressType == TrojanAddressType.DOMAIN) {
            out.writeByte(dstAddr.length());
            out.writeCharSequence(dstAddr, StandardCharsets.UTF_8);
        } else {
            //TODO 暂时不支持ipV6
            throw new RuntimeException("无法支持的地址类型");
        }
    }
}

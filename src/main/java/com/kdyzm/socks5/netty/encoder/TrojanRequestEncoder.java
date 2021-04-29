package com.kdyzm.socks5.netty.encoder;

import com.kdyzm.socks5.netty.constants.TrojanAddressType;
import com.kdyzm.socks5.netty.models.TrojanRequest;
import com.kdyzm.socks5.netty.models.TrojanWrapperRequest;
import com.kdyzm.socks5.netty.util.Sha256Util;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.Charset;
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
     *
     * @param ctx
     * @param msg
     * @param out
     * @throws Exception
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, TrojanWrapperRequest msg, ByteBuf out) throws Exception {
        log.debug("请求代理服务器发起trojan协议握手");
        String password = Sha256Util.encryptThisString(msg.getPassword());
        TrojanRequest trojanRequest = msg.getTrojanRequest();
        out.writeCharSequence(password, StandardCharsets.UTF_8);
        out.writeByte(0X0D0A);
        out.writeByte(trojanRequest.getCmd());
        out.writeByte(trojanRequest.getAtyp());
        encodeAddress(trojanRequest.getAtyp(), out, trojanRequest.getDstAddr());
        out.writeShort(trojanRequest.getDstPort());
        out.writeByte(0X0D0A);
        ctx.pipeline().remove(this);
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
            throw new RuntimeException("无法支持的地址类型");
        }
    }
}

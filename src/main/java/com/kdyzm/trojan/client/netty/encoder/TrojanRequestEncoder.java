package com.kdyzm.trojan.client.netty.encoder;

import com.kdyzm.trojan.client.netty.util.Sha224Util;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

/**
 * trojan 握手帧编码器。
 * 目标元数据在构造时注入（Phase 2 收敛：不再依赖首帧 wrapper 消息携带）。
 * 状态机 INIT/SUCCESS 保证只在第一个消息编码时输出握手头（含首段 payload），
 * 后续消息原样透传。握手头触发时机 = 出站连接上第一个业务数据（与历史行为一致）。
 *
 * <pre>
 * +-----------------------+---------+----------------+---------+----------+
 * | hex(SHA224(password)) |  CRLF   | Trojan Request |  CRLF   | Payload  |
 * +-----------------------+---------+----------------+---------+----------+
 * Trojan Request: CMD(0x01) ATYP DST.ADDR DST.PORT(big-endian)
 * </pre>
 */
@Slf4j
public class TrojanRequestEncoder extends MessageToByteEncoder<ByteBuf> {

    private final String password;

    private final int atyp;

    private final String dstAddr;

    private final int dstPort;

    enum State {
        INIT,
        SUCCESS
    }

    private State state;

    public TrojanRequestEncoder(String password, int atyp, String dstAddr, int dstPort) {
        this.password = password;
        this.atyp = atyp;
        this.dstAddr = dstAddr;
        this.dstPort = dstPort;
        this.state = State.INIT;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        if (state == State.INIT) {
            log.debug("trojan协议初次握手");
            String hash = Sha224Util.encryptThisString(password);
            out.writeCharSequence(hash, StandardCharsets.UTF_8);
            out.writeByte(0X0D);
            out.writeByte(0X0A);
            out.writeByte(0X01); // CMD = CONNECT
            out.writeByte(atyp);
            encodeAddress(atyp, out, dstAddr);
            out.writeShort(dstPort);
            out.writeByte(0X0D);
            out.writeByte(0X0A);
            state = State.SUCCESS;
        }
        //SUCCESS 与 INIT 均需写 payload（INIT 时 payload 紧跟握手头）
        out.writeBytes(msg, msg.readerIndex(), msg.readableBytes());
    }

    /**
     * 编码目标地址：ATYP=1 IPv4 四字节、ATYP=3 域名（长度前缀）、ATYP=4 IPv6 十六字节
     */
    private void encodeAddress(int addressType, ByteBuf out, String dstAddr) {
        if (addressType == 0X01) {
            String[] split = dstAddr.split("\\.");
            for (String item : split) {
                out.writeByte(Integer.parseInt(item));
            }
        } else if (addressType == 0X03) {
            out.writeByte(dstAddr.length());
            out.writeCharSequence(dstAddr, StandardCharsets.UTF_8);
        } else if (addressType == 0X04) {
            try {
                byte[] bytes = InetAddress.getByName(dstAddr).getAddress();
                if (bytes.length != 16) {
                    throw new IllegalArgumentException("非法的 IPv6 地址: " + dstAddr);
                }
                out.writeBytes(bytes);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("非法的 IPv6 地址: " + dstAddr, e);
            }
        } else {
            throw new IllegalArgumentException("无法支持的地址类型: " + addressType);
        }
    }
}

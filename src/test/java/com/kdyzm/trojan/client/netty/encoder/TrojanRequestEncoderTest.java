package com.kdyzm.trojan.client.netty.encoder;

import com.kdyzm.trojan.client.netty.models.TrojanRequest;
import com.kdyzm.trojan.client.netty.models.TrojanWrapperRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import org.junit.Test;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * trojan 握手帧编码测试：密码头(56 hex + CRLF)、CMD、ATYP、地址、端口、CRLF、payload
 */
public class TrojanRequestEncoderTest {

    private static final String PASSWORD = "password";
    /** SHA224("password") 的 hex，由 Python 独立生成 */
    private static final String PASSWORD_HASH = "d63dc919e201d7bc4c825630d2cf25fdc93d4b2f0d46706d29038d01";

    private ByteBuf encode(int atyp, String dstAddr, int dstPort, String payload) throws Exception {
        TrojanRequest trojanRequest = new TrojanRequest();
        trojanRequest.setCmd(0x01);
        trojanRequest.setAtyp(atyp);
        trojanRequest.setDstAddr(dstAddr);
        trojanRequest.setDstPort(dstPort);
        TrojanWrapperRequest wrapper = new TrojanWrapperRequest();
        wrapper.setPassword(PASSWORD);
        wrapper.setTrojanRequest(trojanRequest);
        wrapper.setPayload(Unpooled.wrappedBuffer(payload.getBytes(StandardCharsets.UTF_8)));

        EmbeddedChannel channel = new EmbeddedChannel(new TrojanRequestEncoder());
        channel.writeOutbound(wrapper);
        ByteBuf out = channel.readOutbound();
        assertTrue("握手帧应被写出", out != null);
        return out;
    }

    @Test
    public void testIpv6Address() throws Exception {
        ByteBuf out = encode(Socks5AddressType.IPv6.byteValue(), "::1", 443, "hi");
        // 密码头(56 hex) + CRLF
        out.skipBytes(PASSWORD_HASH.length() + 2);
        assertEquals(0x01, out.readByte()); // CMD = CONNECT
        assertEquals(0x04, out.readByte()); // ATYP = IPv6
        byte[] addr = new byte[16];
        out.readBytes(addr);
        assertArrayEquals(InetAddress.getByName("::1").getAddress(), addr);
        assertEquals(443, out.readUnsignedShort());
        out.skipBytes(2); // CRLF
        assertEquals("hi", out.readCharSequence(2, StandardCharsets.UTF_8).toString());
        out.release();
    }

    @Test
    public void testDomainNameAddress() throws Exception {
        ByteBuf out = encode(Socks5AddressType.DOMAIN.byteValue(), "example.com", 80, "hi");
        out.skipBytes(PASSWORD_HASH.length() + 2);
        assertEquals(0x01, out.readByte()); // CMD = CONNECT
        assertEquals(0x03, out.readByte()); // ATYP = DOMAIN
        assertEquals(11, out.readUnsignedByte()); // "example.com" 长度
        assertEquals("example.com", out.readCharSequence(11, StandardCharsets.UTF_8).toString());
        assertEquals(80, out.readUnsignedShort());
        out.release();
    }

    @Test
    public void testIpv4Address() throws Exception {
        ByteBuf out = encode(Socks5AddressType.IPv4.byteValue(), "1.2.3.4", 8080, "hi");
        out.skipBytes(PASSWORD_HASH.length() + 2);
        assertEquals(0x01, out.readByte()); // CMD = CONNECT
        assertEquals(0x01, out.readByte()); // ATYP = IPv4
        assertEquals(1, out.readUnsignedByte());
        assertEquals(2, out.readUnsignedByte());
        assertEquals(3, out.readUnsignedByte());
        assertEquals(4, out.readUnsignedByte());
        assertEquals(8080, out.readUnsignedShort());
        out.release();
    }
}

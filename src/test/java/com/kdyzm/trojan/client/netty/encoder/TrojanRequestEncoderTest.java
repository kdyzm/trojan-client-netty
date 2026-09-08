package com.kdyzm.trojan.client.netty.encoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * trojan 握手帧编码测试：密码头(56 hex + CRLF)、CMD、ATYP、地址、端口、CRLF、payload
 * 元数据经构造注入，首个数据消息触发完整握手头
 */
public class TrojanRequestEncoderTest {

    private static final String PASSWORD = "password";
    /** SHA224("password") 的 hex，由 Python 独立生成 */
    private static final String PASSWORD_HASH = "d63dc919e201d7bc4c825630d2cf25fdc93d4b2f0d46706d29038d01";

    private ByteBuf encode(int atyp, String dstAddr, int dstPort, String payload) {
        TrojanRequestEncoder encoder = new TrojanRequestEncoder(PASSWORD, atyp, dstAddr, dstPort);
        EmbeddedChannel channel = new EmbeddedChannel(encoder);
        channel.writeOutbound(Unpooled.wrappedBuffer(payload.getBytes(StandardCharsets.UTF_8)));
        ByteBuf out = channel.readOutbound();
        if (out == null) {
            throw new AssertionError("编码帧应被写出");
        }
        return out;
    }

    @Test
    public void testIpv6Address() throws Exception {
        ByteBuf out = encode(0x04, "::1", 443, "hi");
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
    public void testDomainNameAddress() {
        ByteBuf out = encode(0x03, "example.com", 80, "hi");
        out.skipBytes(PASSWORD_HASH.length() + 2);
        assertEquals(0x01, out.readByte());
        assertEquals(0x03, out.readByte());
        assertEquals(11, out.readUnsignedByte()); // "example.com" 长度
        assertEquals("example.com", out.readCharSequence(11, StandardCharsets.UTF_8).toString());
        assertEquals(80, out.readUnsignedShort());
        out.release();
    }

    @Test
    public void testIpv4Address() {
        ByteBuf out = encode(0x01, "1.2.3.4", 8080, "hi");
        out.skipBytes(PASSWORD_HASH.length() + 2);
        assertEquals(0x01, out.readByte());
        assertEquals(0x01, out.readByte());
        assertEquals(1, out.readUnsignedByte());
        assertEquals(2, out.readUnsignedByte());
        assertEquals(3, out.readUnsignedByte());
        assertEquals(4, out.readUnsignedByte());
        assertEquals(8080, out.readUnsignedShort());
        out.release();
    }

    @Test
    public void testHandshakeOnlyOnFirstMessage() {
        // 第二个消息不应重复输出握手头
        TrojanRequestEncoder encoder = new TrojanRequestEncoder(PASSWORD, 0x03, "example.com", 80);
        EmbeddedChannel channel = new EmbeddedChannel(encoder);
        channel.writeOutbound(Unpooled.wrappedBuffer("first".getBytes()));
        ByteBuf first = channel.readOutbound();
        first.skipBytes(PASSWORD_HASH.length() + 2 + 1 + 1 + 1);
        assertEquals("example.com", first.readCharSequence(11, StandardCharsets.UTF_8).toString());
        assertEquals(80, first.readUnsignedShort());
        first.release();

        channel.writeOutbound(Unpooled.wrappedBuffer("second".getBytes()));
        ByteBuf second = channel.readOutbound();
        assertEquals("second", second.toString(StandardCharsets.UTF_8));
        second.release();
        channel.finishAndReleaseAll();
    }
}

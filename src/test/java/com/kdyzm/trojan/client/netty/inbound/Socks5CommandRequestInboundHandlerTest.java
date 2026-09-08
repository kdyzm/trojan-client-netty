package com.kdyzm.trojan.client.netty.inbound;

import com.kdyzm.trojan.client.netty.models.PacModel;
import com.kdyzm.trojan.client.netty.properties.ConfigProperties;
import com.kdyzm.trojan.client.netty.router.ProxyRouter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 不支持的 SOCKS5 命令（UDP ASSOCIATE / BIND）应返回 COMMAND_UNSUPPORTED 并关闭连接
 */
public class Socks5CommandRequestInboundHandlerTest {

    private Socks5CommandRequestInboundHandler newHandler() {
        ConfigProperties configProperties = new ConfigProperties();
        configProperties.setProxyMode("global");
        return new Socks5CommandRequestInboundHandler(null,
                new ProxyRouter(new HashMap<String, PacModel>(), "global"),
                configProperties, new RelayHandler());
    }

    @Test
    public void testUdpAssociateRejected() {
        EmbeddedChannel channel = new EmbeddedChannel(newHandler());
        DefaultSocks5CommandRequest request = new DefaultSocks5CommandRequest(
                Socks5CommandType.UDP_ASSOCIATE, Socks5AddressType.DOMAIN, "example.com", 53);
        channel.writeInbound(request);

        DefaultSocks5CommandResponse response = channel.readOutbound();
        assertEquals(Socks5CommandStatus.COMMAND_UNSUPPORTED, response.status());
        assertFalse("回复不支持后应关闭连接", channel.isOpen());
        channel.finishAndReleaseAll();
    }

    @Test
    public void testBindRejected() {
        EmbeddedChannel channel = new EmbeddedChannel(newHandler());
        DefaultSocks5CommandRequest request = new DefaultSocks5CommandRequest(
                Socks5CommandType.BIND, Socks5AddressType.IPv4, "127.0.0.1", 8080);
        channel.writeInbound(request);

        DefaultSocks5CommandResponse response = channel.readOutbound();
        assertEquals(Socks5CommandStatus.COMMAND_UNSUPPORTED, response.status());
        assertFalse(channel.isOpen());
        channel.finishAndReleaseAll();
    }

    /**
     * Task 3 审查回归：BLOCK 分支需把静态链上的 relay 一并移除。
     * 否则浏览器收到假 SUCCESS 后发的 HTTP 请求先到未激活 relay 被释放，
     * 永远到不了 HttpServerCodec/BlackListInboundHandler，黑名单拦截页不响应。
     * 静态链装配（与 NettyServerInitializer 一致）：encoder + decoder + commandHandler + 同一 relay 实例。
     */
    @Test
    public void testBlockedDomainServesBlacklistPage() {
        ConfigProperties configProperties = new ConfigProperties();
        configProperties.setProxyMode("global");
        Map<String, PacModel> pacMap = new HashMap<String, PacModel>();
        PacModel blocked = new PacModel();
        blocked.setDomainName("blocked.com");
        blocked.setProxyMode(-1);
        pacMap.put("blocked.com", blocked);
        RelayHandler relay = new RelayHandler();
        Socks5CommandRequestInboundHandler handler = new Socks5CommandRequestInboundHandler(null,
                new ProxyRouter(pacMap, "global"), configProperties, relay);
        EmbeddedChannel channel = new EmbeddedChannel(Socks5ServerEncoder.DEFAULT,
                new Socks5CommandRequestDecoder(), handler, relay);
        try {
            //触发 BLOCK 分支：假 SUCCESS + 换 HttpServerCodec/BlackListInboundHandler 链
            channel.writeInbound(new DefaultSocks5CommandRequest(
                    Socks5CommandType.CONNECT, Socks5AddressType.DOMAIN, "blocked.com", 80));

            //修复点：未激活的 relay 必须随握手 handler 一起移除，否则黑名单拦截页数据被 relay 吞掉
            assertNull("BLOCK 后未激活的 relay 应从链上移除", channel.pipeline().get(RelayHandler.class));

            //模拟浏览器收到假 SUCCESS 后发送的真实 HTTP 请求（原始字节）
            ByteBuf httpRequest = Unpooled.copiedBuffer(
                    "GET / HTTP/1.1\r\nHost: blocked.com\r\n\r\n", CharsetUtil.UTF_8);
            channel.writeInbound(httpRequest);

            //第一个出站对象为假 SUCCESS：SOCKS5 协议版本 + SUCCESS 状态（Socks5ServerEncoder 编码后为 ByteBuf）
            Object first = channel.readOutbound();
            assertTrue("BLOCK 分支应先回假 SUCCESS 响应", first instanceof ByteBuf);
            ByteBuf successBuf = (ByteBuf) first;
            assertEquals(0x05, successBuf.getByte(0));
            assertEquals(Socks5CommandStatus.SUCCESS.byteValue(), successBuf.getByte(1));
            successBuf.release();

            //随后应出现黑名单拦截页的 HTTP 200 响应——证明请求到达了 BlackListInboundHandler 而非被未激活 relay 释放
            //（BlackList 的 DefaultHttpResponse 经 HttpServerCodec 编码后以 ByteBuf 形态出站）
            boolean httpOkFound = false;
            Object out;
            while ((out = channel.readOutbound()) != null) {
                if (out instanceof ByteBuf) {
                    String content = ((ByteBuf) out).toString(CharsetUtil.UTF_8);
                    if (content.startsWith("HTTP/1.1 200 OK")) {
                        httpOkFound = true;
                    }
                }
                ReferenceCountUtil.release(out);
            }
            assertTrue("黑名单拦截页未响应：HTTP 请求被未激活的 relay 吞掉（回归）", httpOkFound);
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}

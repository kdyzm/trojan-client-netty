package com.kdyzm.trojan.client.netty.inbound;

import com.kdyzm.trojan.client.netty.models.PacModel;
import com.kdyzm.trojan.client.netty.properties.ConfigProperties;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * 不支持的 SOCKS5 命令（UDP ASSOCIATE / BIND）应返回 COMMAND_UNSUPPORTED 并关闭连接
 */
public class Socks5CommandRequestInboundHandlerTest {

    private Socks5CommandRequestInboundHandler newHandler() {
        ConfigProperties configProperties = new ConfigProperties();
        configProperties.setProxyMode("global");
        return new Socks5CommandRequestInboundHandler(null, new HashMap<String, PacModel>(), configProperties);
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
}

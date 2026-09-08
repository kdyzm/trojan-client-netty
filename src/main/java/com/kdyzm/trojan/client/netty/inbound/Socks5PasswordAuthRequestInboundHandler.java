package com.kdyzm.trojan.client.netty.inbound;

import com.kdyzm.trojan.client.netty.properties.ConfigUtil;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * @author kdyzm
 * @date 2021/4/25
 */
@Slf4j
@AllArgsConstructor
public class Socks5PasswordAuthRequestInboundHandler extends SimpleChannelInboundHandler<DefaultSocks5PasswordAuthRequest> {

    private final ConfigUtil configUtil;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5PasswordAuthRequest msg) throws Exception {
        //认证成功
        if (authenticate(configUtil.getUsers(), msg.username(), msg.password())) {
            Socks5PasswordAuthResponse passwordAuthResponse = new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS);
            ctx.writeAndFlush(passwordAuthResponse);
            ctx.pipeline().remove(this);
            ctx.pipeline().remove(Socks5PasswordAuthRequestDecoder.class);
            return;
        }
        Socks5PasswordAuthResponse passwordAuthResponse = new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE);
        //发送鉴权失败消息，完成后关闭channel
        ctx.writeAndFlush(passwordAuthResponse).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * 校验用户名密码：用户名密码均做 trim，用户名不存在时安全返回 false（不抛 NPE）
     *
     * @param users    users.properties 解析出的授权用户表
     * @param username 客户端上报的用户名（可为 null）
     * @param password 客户端上报的密码（可为 null）
     */
    static boolean authenticate(Map<String, String> users, String username, String password) {
        if (users == null || username == null || password == null) {
            return false;
        }
        String stored = users.get(username.trim());
        return stored != null && stored.equals(password.trim());
    }
}

package com.kdyzm.trojan.client.netty.util;

import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.util.NetUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * @author kdyzm
 * @date 2021/5/15
 */
@Slf4j
public class IpUtil {

    /**
     * 判定一个地址是什么类型的地址
     *
     * @param host 地址字符串
     * @return 映射的socks地址类型
     * @see Socks5AddressType
     */
    public static Socks5AddressType parseAddress(String host) {
        if (NetUtil.isValidIpV4Address(host)) {
            return Socks5AddressType.IPv4;
        }
        if (NetUtil.isValidIpV6Address(host)) {
            return Socks5AddressType.IPv6;
        }
        return Socks5AddressType.DOMAIN;
    }
}

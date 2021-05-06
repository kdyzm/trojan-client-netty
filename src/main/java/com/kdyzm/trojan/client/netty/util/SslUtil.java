package com.kdyzm.trojan.client.netty.util;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;

/**
 * @author kdyzm
 * @date 2021/5/6
 */
public class SslUtil {

    private static SslContext sslContext = null;

    private static final Logger log = LoggerFactory.getLogger(SslUtil.class);

    static {
        try {
            sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        } catch (SSLException e) {
            log.error("", e);
        }
    }

    public static SslContext getContext() {
        return sslContext;
    }
}

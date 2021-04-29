package com.kdyzm.socks5.netty.properties;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * @author kdyzm
 * @date 2021-04-24
 */
@Data
@Component
@PropertySource(value = {"file:config.properties"})
public class ConfigProperties {

    /**
     * 是否认证的开关
     */
    @Value("${authentication}")
    private boolean authentication;

    /**
     * 服务绑定的端口号
     */
    @Value("${server.port}")
    private Integer serverPort;

    /**
     * 认证文件路径
     */
    @Value("${authentication.path}")
    private String authenticationPath;

    /**
     * 黑名单路径
     */
    @Value("${blacklist.path}")
    private String blacklistPath;

    /**
     * 是否启用trojan代理
     */
    @Value("${trojan.enabel}")
    private boolean trojanEnable;

    /**
     * trojan服务器地址
     */
    @Value("${trojan.server.host}")
    private String trojanServerHost;

    /**
     * trojan服务器端口号
     */
    @Value("${trojan.server.port}")
    private int trojanServerPort;

    /**
     * trojan密码
     */
    @Value("${trojan.password}")
    private String trojanPassword;
}

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
}

package com.kdyzm.trojan.client.netty.properties;

import com.kdyzm.trojan.client.netty.factory.YamlPropertySourceFactory;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * @author kdyzm
 * @date 2021-04-24
 */
@Data
@Component
@PropertySource(value = {"file:config.yml"},factory = YamlPropertySourceFactory.class)
public class ConfigProperties {

    /**
     * 运行模式
     * <ul>
     *     <li>global：全局代理模式</li>
     *     <li>pac：白名单模式</li>
     *     <li>direct：直连模式</li>
     * </ul>
     */
    @Value("${proxy.mode}")
    private String proxyMode;
    
    /**
     * pac文件的路径
     */
    @Value("${pacfile.path}")
    private String pacFilePath;

    /**
     * 是否认证的开关
     */
    @Value("${socks5.authentication.enabel}")
    private boolean authentication;

    /**
     * 服务绑定的端口号
     */
    @Value("${server.port}")
    private Integer serverPort;

    /**
     * 认证文件路径
     */
    @Value("${socks5.authentication.path}")
    private String authenticationPath;

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

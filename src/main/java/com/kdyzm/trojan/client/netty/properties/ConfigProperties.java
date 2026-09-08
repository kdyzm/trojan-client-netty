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
@PropertySource(value = {"file:config.yml"}, factory = YamlPropertySourceFactory.class)
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
     * http 代理端口的认证开关（Proxy-Authorization Basic，凭据复用 users.properties）。
     * 默认关闭；如需开启请在 config.yml 添加 http.authentication.enabel: true（拼写沿用 socks5 的既有写法）
     */
    @Value("${http.authentication.enabel:false}")
    private boolean httpAuthentication;

    /**
     * 服务绑定的端口号
     */
    @Value("${server.port.socks5}")
    private Integer socks5Port;

    @Value("${server.port.http}")
    private Integer httpPort;

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

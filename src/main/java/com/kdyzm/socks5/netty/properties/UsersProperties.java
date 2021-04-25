package com.kdyzm.socks5.netty.properties;

import com.kdyzm.socks5.netty.models.UsernameAndPasswordModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * @author kdyzm
 * @date 2021/4/25
 */
@Component
@Slf4j
public class UsersProperties {

    private Map<String, String> users = new HashMap<>();

    private ConfigProperties configProperties;

    @Autowired
    public UsersProperties(ConfigProperties configProperties) {
        this.configProperties = configProperties;
    }

    /**
     * 从配置文件中读取用户名和密码配置
     *
     * @return
     */
    public Map<String, String> getUsers() throws IOException {
        if (!CollectionUtils.isEmpty(users)) {
            return users;
        }
        synchronized (this) {
            if (!CollectionUtils.isEmpty(users)) {
                return users;
            }
            String authenticationPath = configProperties.getAuthenticationPath();
            Properties properties = new Properties();
            properties.load(new FileReader(authenticationPath));
            properties.forEach((key, value) -> users.put(key.toString().trim(), value.toString().trim()));
            return users;
        }
    }


}

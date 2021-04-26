package com.kdyzm.socks5.netty.properties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.*;
import java.util.*;

/**
 * @author kdyzm
 * @date 2021/4/25
 */
@Component
@Slf4j
public class ConfigUtil {

    private Map<String, String> users = new HashMap<>();

    private ConfigProperties configProperties;

    private Set<String> blackList = new HashSet<>();

    @Autowired
    public ConfigUtil(ConfigProperties configProperties) {
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

    /**
     * 从配置文件中读取用户名和密码配置
     *
     * @return list
     */
    public Set<String> getBlackList() throws IOException {
        if (!CollectionUtils.isEmpty(blackList)) {
            return blackList;
        }
        synchronized (this) {
            if (!CollectionUtils.isEmpty(blackList)) {
                return blackList;
            }
            String blacklistPath = configProperties.getBlacklistPath();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(blacklistPath)));
            String str;
            while ((str = bufferedReader.readLine()) != null) {
                blackList.add(str.toLowerCase(Locale.ROOT));
            }
            return blackList;
        }
    }


}

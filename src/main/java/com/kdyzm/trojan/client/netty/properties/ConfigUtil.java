package com.kdyzm.trojan.client.netty.properties;

import com.kdyzm.trojan.client.netty.models.PacModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

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

    private Map<String, PacModel> pacModelMap = new HashMap<>();
    
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
    
    public Map<String,PacModel> getPacModelMap() throws IOException {
        if (!CollectionUtils.isEmpty(pacModelMap)) {
            return pacModelMap;
        }
        synchronized (this) {
            if (!CollectionUtils.isEmpty(pacModelMap)) {
                return pacModelMap;
            }
            String pacFilePath = configProperties.getPacFilePath();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(pacFilePath)));
            String str;
            while ((str = bufferedReader.readLine()) != null) {
                String s = str.toLowerCase(Locale.ROOT).replaceAll("\\s*|\t", "");
                if(StringUtils.isEmpty(s) || s.startsWith("#")){
                    continue;
                }
                String[] split = s.split(":");
                PacModel pacModel = new PacModel();
                pacModel.setDomainName(split[0]);
                pacModel.setProxyMode(Integer.parseInt(split[1]));
                pacModelMap.put(split[0],pacModel);
            }
            return pacModelMap;
        }
    }
}

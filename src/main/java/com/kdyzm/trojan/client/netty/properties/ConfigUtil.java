package com.kdyzm.trojan.client.netty.properties;

import com.kdyzm.trojan.client.netty.models.PacModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * 用户表与 pac 名单的加载器：懒加载 + 文件 mtime 检测热更新。
 * 每次调用检查文件 lastModified，变更即全量重载并替换内存 map；
 * 文件缺失或解析失败时保留旧数据并告警（首次加载失败则抛出，由调用方决定）。
 * 方法整体 synchronized——调用频率低（每连接/每认证一次），换取无 volatile 的正确性。
 *
 * @author kdyzm
 * @date 2021/4/25
 */
@Component
@Slf4j
public class ConfigUtil {

    private Map<String, String> users = new HashMap<>();

    private long usersLastModified = -1;

    private Map<String, PacModel> pacModelMap = new HashMap<>();

    private long pacLastModified = -1;

    private final ConfigProperties configProperties;

    @Autowired
    public ConfigUtil(ConfigProperties configProperties) {
        this.configProperties = configProperties;
    }

    /**
     * 读取用户名密码（文件变更时自动重载）
     *
     * @return 用户名 → 密码（均已 trim）
     */
    public synchronized Map<String, String> getUsers() throws IOException {
        File file = new File(configProperties.getAuthenticationPath());
        long modified = file.lastModified();
        if (modified != usersLastModified || usersLastModified == -1) {
            if (modified == 0L) {
                if (usersLastModified == -1) {
                    throw new IOException("用户文件不存在: " + configProperties.getAuthenticationPath());
                }
                //文件被删除：保留旧数据
                log.warn("用户文件不存在，保留上次加载的数据: {}", configProperties.getAuthenticationPath());
                usersLastModified = 0L;
                return users;
            }
            Properties properties = new Properties();
            try (Reader reader = new FileReader(file)) {
                properties.load(reader);
            } catch (IOException | IllegalArgumentException e) {
                if (usersLastModified == -1) {
                    if (e instanceof IOException) {
                        throw (IOException) e;
                    }
                    //首载即畸形内容：包装后快速失败
                    throw new IOException("用户文件解析失败: " + configProperties.getAuthenticationPath(), e);
                }
                //读取/解析失败：保留旧数据
                log.warn("读取用户文件失败，保留上次加载的数据: {}", configProperties.getAuthenticationPath(), e);
                usersLastModified = modified;
                return users;
            }
            Map<String, String> fresh = new HashMap<>();
            properties.forEach((key, value) -> fresh.put(key.toString().trim(), value.toString().trim()));
            users = fresh;
            usersLastModified = modified;
            log.info("用户配置已加载/更新，共 {} 个用户", users.size());
        }
        return users;
    }

    /**
     * 读取 pac 名单（文件变更时自动重载）。每行 `域名:模式值`，支持 # 注释与空白行
     *
     * @return 域名(小写) → PacModel
     */
    public synchronized Map<String, PacModel> getPacModelMap() throws IOException {
        File file = new File(configProperties.getPacFilePath());
        long modified = file.lastModified();
        if (modified != pacLastModified || pacLastModified == -1) {
            if (modified == 0L) {
                if (pacLastModified == -1) {
                    throw new IOException("pac 文件不存在: " + configProperties.getPacFilePath());
                }
                log.warn("pac 文件不存在，保留上次加载的数据: {}", configProperties.getPacFilePath());
                pacLastModified = 0L;
                return pacModelMap;
            }
            Map<String, PacModel> fresh = new HashMap<>();
            try (BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String str;
                while ((str = bufferedReader.readLine()) != null) {
                    String s = str.toLowerCase(Locale.ROOT).replaceAll("\\s*|\t", "");
                    if (StringUtils.isEmpty(s) || s.startsWith("#")) {
                        continue;
                    }
                    String[] split = s.split(":");
                    PacModel pacModel = new PacModel();
                    pacModel.setDomainName(split[0]);
                    pacModel.setProxyMode(Integer.parseInt(split[1]));
                    fresh.put(split[0], pacModel);
                }
            } catch (IOException | NumberFormatException | ArrayIndexOutOfBoundsException e) {
                if (pacLastModified == -1) {
                    if (e instanceof IOException) {
                        throw (IOException) e;
                    }
                    throw new IOException("pac 文件解析失败: " + configProperties.getPacFilePath(), e);
                }
                //读取/解析失败：保留旧数据
                log.warn("读取 pac 文件失败，保留上次加载的数据: {}", configProperties.getPacFilePath(), e);
                pacLastModified = modified;
                return pacModelMap;
            }
            pacModelMap = fresh;
            pacLastModified = modified;
            log.info("pac 名单已加载/更新，共 {} 条", pacModelMap.size());
        }
        return pacModelMap;
    }
}

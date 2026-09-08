package com.kdyzm.trojan.client.netty.properties;

import com.kdyzm.trojan.client.netty.models.PacModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 名单/用户热更新测试：文件修改后再次读取可见新内容；文件删除后保留旧数据
 */
public class ConfigUtilTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private void write(File file, String content) throws Exception {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    private ConfigProperties properties(String pacPath, String usersPath) {
        ConfigProperties props = new ConfigProperties();
        props.setPacFilePath(pacPath);
        props.setAuthenticationPath(usersPath);
        return props;
    }

    @Test
    public void testPacReloadAfterFileChange() throws Exception {
        File pac = tempFolder.newFile("pac.txt");
        write(pac, "baidu.com:1\ngoogle.com:0\n");
        ConfigUtil configUtil = new ConfigUtil(properties(pac.getAbsolutePath(), "unused"));

        Map<String, PacModel> first = configUtil.getPacModelMap();
        assertEquals(1, first.get("baidu.com").getProxyMode());

        //修改文件内容后，再次读取应看到新名单
        Thread.sleep(1100); //文件系统 mtime 粒度兜底（FAT/NTFS 为 2s 粒度——若仍失败改为 2100ms）
        write(pac, "example.com:-1\n");
        Map<String, PacModel> second = configUtil.getPacModelMap();
        assertEquals(1, second.size());
        assertEquals(-1, second.get("example.com").getProxyMode());
        assertTrue("重载后返回新 map 实例", first != second);
    }

    @Test
    public void testUsersReloadAfterFileChange() throws Exception {
        File users = tempFolder.newFile("users.properties");
        write(users, "alice=secret123\n");
        ConfigUtil configUtil = new ConfigUtil(properties("unused", users.getAbsolutePath()));

        Map<String, String> first = configUtil.getUsers();
        assertEquals("secret123", first.get("alice"));

        Thread.sleep(1100);
        write(users, "alice=newpass\nbob=pass2\n");
        Map<String, String> second = configUtil.getUsers();
        //文件变更后再次读取应可见新用户表
        assertEquals("newpass", second.get("alice"));
        assertEquals("pass2", second.get("bob"));
        assertTrue("重载后返回新 map 实例", first != second);
    }

    @Test
    public void testPacDeletedKeepsOldData() throws Exception {
        File pac = tempFolder.newFile("pac.txt");
        write(pac, "baidu.com:1\n");
        ConfigUtil configUtil = new ConfigUtil(properties(pac.getAbsolutePath(), "unused"));
        Map<String, PacModel> first = configUtil.getPacModelMap();
        assertEquals(1, first.size());

        //删除文件后：保留旧数据而非报错
        assertTrue(pac.delete());
        Map<String, PacModel> second = configUtil.getPacModelMap();
        assertFalse("文件删除后应保留旧名单", second.isEmpty());
        assertEquals(1, second.get("baidu.com").getProxyMode());
    }

    @Test
    public void testPacMalformedLineKeepsOldData() throws Exception {
        File pac = tempFolder.newFile("pac.txt");
        write(pac, "baidu.com:1\n");
        ConfigUtil configUtil = new ConfigUtil(properties(pac.getAbsolutePath(), "unused"));
        Map<String, PacModel> first = configUtil.getPacModelMap();
        assertEquals(1, first.size());

        Thread.sleep(1100);
        //无冒号的坏行：修复前 AIOOBE 逃逸 catch，直接抛异常阻断新连接
        write(pac, "baidu.com:1\nexample.com\n");
        Map<String, PacModel> second = configUtil.getPacModelMap();
        assertFalse("坏行应被忽略并保留旧数据，而非抛异常", second.isEmpty());
        assertEquals("旧数据应保留", 1, second.get("baidu.com").getProxyMode());
    }

    @Test
    public void testUsersMalformedEscapeKeepsOldData() throws Exception {
        File users = tempFolder.newFile("users.properties");
        write(users, "alice=secret123\n");
        ConfigUtil configUtil = new ConfigUtil(properties("unused", users.getAbsolutePath()));
        Map<String, String> first = configUtil.getUsers();
        assertEquals("secret123", first.get("alice"));

        Thread.sleep(1100);
        //截断的 unicode 转义(内容为 bob=反斜杠u12)：修复前 IllegalArgumentException 逃逸 catch
        //注:源码中不可出现裸反斜杠+u(编译器 Unicode 预处理),此处用字符拼接生成反斜杠
        write(users, "alice=secret123\nbob=" + '\\' + "u12\n");
        Map<String, String> second = configUtil.getUsers();
        assertFalse("畸形内容应保留旧数据，而非抛异常", second.isEmpty());
        assertEquals("secret123", second.get("alice"));
    }
}

package com.kdyzm.trojan.client.netty.inbound;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static com.kdyzm.trojan.client.netty.inbound.Socks5PasswordAuthRequestInboundHandler.authenticate;

/**
 * 认证判定逻辑测试：覆盖用户名不存在、空值、空白字符等场景
 */
public class Socks5PasswordAuthRequestInboundHandlerTest {

    private Map<String, String> users;

    @Before
    public void setUp() {
        users = new HashMap<>();
        users.put("alice", "secret123");
    }

    @Test
    public void testValidCredential() {
        assertTrue(authenticate(users, "alice", "secret123"));
    }

    @Test
    public void testUnknownUsernameDoesNotThrow() {
        // 回归：修复前 users.get 返回 null 直接 NPE
        assertFalse(authenticate(users, "bob", "secret123"));
    }

    @Test
    public void testWrongPassword() {
        assertFalse(authenticate(users, "alice", "wrong"));
    }

    @Test
    public void testUsernameAndPasswordTrimmed() {
        assertTrue(authenticate(users, "  alice  ", "  secret123  "));
    }

    @Test
    public void testNullArguments() {
        assertFalse(authenticate(null, "alice", "secret123"));
        assertFalse(authenticate(users, null, "secret123"));
        assertFalse(authenticate(users, "alice", null));
    }
}

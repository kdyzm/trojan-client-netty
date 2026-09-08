package com.kdyzm.trojan.client.netty.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * SHA-224 摘要算法的回归测试
 * 向量由 Python hashlib 独立生成，防止加密逻辑被无意改动
 *
 * @author kdyzm
 * @date 2026-09-08
 */
public class Sha224UtilTest {

    @Test
    public void testEncryptKnownVectorPassword() {
        assertEquals("d63dc919e201d7bc4c825630d2cf25fdc93d4b2f0d46706d29038d01",
                Sha224Util.encryptThisString("password"));
    }

    @Test
    public void testEncryptKnownVectorTrojan() {
        String hash = Sha224Util.encryptThisString("trojan");
        // trojan 协议要求 56 位小写十六进制
        assertEquals(56, hash.length());
        assertEquals(hash.toLowerCase(), hash);
        assertEquals("07f3019e36783ac5253649a9aa621ba3287b0b7b4e8db6c5c5519444", hash);
    }
}

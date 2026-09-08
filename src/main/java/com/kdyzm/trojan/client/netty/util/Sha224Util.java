package com.kdyzm.trojan.client.netty.util;

import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author kdyzm
 * @date 2021/4/28
 */
@Slf4j
public class Sha224Util {

    public static String encryptThisString(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-224");
            byte[] messageDigest = md.digest(input.getBytes());
            BigInteger no = new BigInteger(1, messageDigest);
            StringBuilder hashtext = new StringBuilder(no.toString(16));
            // SHA-224 摘要固定 28 字节 = 56 位十六进制；BigInteger.toString(16) 会去掉首字节的前导 0，
            // 补足到 56 位以符合 trojan 协议握手头的 56 位小写 hex 约定
            while (hashtext.length() < 56) {
                hashtext.insert(0, "0");
            }
            return hashtext.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}

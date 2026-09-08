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
            //SHA-224 输出 28 字节 = 56 位十六进制；BigInteger.toString(16) 会剥掉全部前导零字节，需统一补足到 56 位
            while (hashtext.length() < 56) {
                hashtext.insert(0, "0");
            }
            return hashtext.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}

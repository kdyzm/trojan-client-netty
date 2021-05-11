package com.kdyzm.trojan.client.netty.enums;

import java.util.Arrays;

/**
 * @author kdyzm
 * @date 2021/5/11
 */
public enum ProxyModelEnum {

    /**
     * 全局代理模式：全部请求都走代理
     */
    GLOBAL("global", "全局代理模式"),

    /**
     * 白名单代理模式：在名单中的走代理
     */
    PAC("pac", "白名单代理模式"),

    /**
     * 直连模式：全部请求直连，不走代理
     */
    DIRECT("direct", "直连模式");


    private final String mode;

    private final String desc;

    ProxyModelEnum(String mode, String desc) {
        this.mode = mode;
        this.desc = desc;
    }

    public String mode() {
        return mode;
    }

    public String desc() {
        return desc;
    }

    public static ProxyModelEnum get(String mode) {
        return Arrays.stream(ProxyModelEnum.values()).filter(item -> item.mode().equalsIgnoreCase(mode)).findFirst().orElse(null);
    }
}

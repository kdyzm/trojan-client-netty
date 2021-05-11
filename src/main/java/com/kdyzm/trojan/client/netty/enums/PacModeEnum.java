package com.kdyzm.trojan.client.netty.enums;

import java.util.Arrays;

/**
 * @author kdyzm
 * @date 2021/5/11
 */
public enum PacModeEnum {

    /**
     * 全局代理模式：全部请求都走代理
     */
    PROXY(1, "代理"),

    /**
     * 拒绝连接：
     */
    BLOCK(-1, "拒绝连接"),

    /**
     * 直连模式：全部请求直连，不走代理
     */
    DIRECT(0, "直连");


    private final int mode;

    private final String desc;

    PacModeEnum(int mode, String desc) {
        this.mode = mode;
        this.desc = desc;
    }

    public int mode() {
        return mode;
    }

    public String desc() {
        return desc;
    }

    public static PacModeEnum get(int mode) {
        return Arrays.stream(PacModeEnum.values()).filter(item -> item.mode() == mode).findFirst().orElse(null);
    }
}

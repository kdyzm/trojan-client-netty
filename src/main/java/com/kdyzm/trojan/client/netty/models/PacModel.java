package com.kdyzm.trojan.client.netty.models;

import com.kdyzm.trojan.client.netty.enums.PacModeEnum;
import lombok.Data;

/**
 * @author kdyzm
 * @date 2021/5/11
 */
@Data
public class PacModel {

    private String domainName;

    /**
     * @see com.kdyzm.trojan.client.netty.enums.PacModeEnum
     */
    private int proxyMode;


    public boolean isDirect() {
        return proxyMode == PacModeEnum.DIRECT.mode();
    }

    public boolean isBlock() {
        return proxyMode == PacModeEnum.BLOCK.mode();
    }

    public boolean isProxy() {
        return proxyMode == PacModeEnum.PROXY.mode();
    }

}

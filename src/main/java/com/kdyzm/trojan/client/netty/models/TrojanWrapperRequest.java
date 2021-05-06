package com.kdyzm.trojan.client.netty.models;

import lombok.Data;

/**
 * @author kdyzm
 * @date 2021/4/28
 */
@Data
public class TrojanWrapperRequest {

    private String password;

    private TrojanRequest trojanRequest;
    
    private Object payload;
}

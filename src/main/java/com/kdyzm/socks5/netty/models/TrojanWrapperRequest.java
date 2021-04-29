package com.kdyzm.socks5.netty.models;

import io.netty.buffer.ByteBuf;
import lombok.Data;

/**
 * @author kdyzm
 * @date 2021/4/28
 */
@Data
public class TrojanWrapperRequest {

    private String password;

    private TrojanRequest trojanRequest;
    
    private ByteBuf payload;
}

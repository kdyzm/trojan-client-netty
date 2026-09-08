package com.kdyzm.trojan.client.netty.models;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Host 头解析结果：host 与端口（-1 表示请求未携带端口，由调用方按协议默认）
 */
@Data
@AllArgsConstructor
public class HostPort {

    private String host;

    private int port;
}

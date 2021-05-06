package com.kdyzm.trojan.client.netty.models;

import lombok.Data;

/**
 * @author kdyzm
 * @date 2021/4/28
 */
@Data
public class TrojanRequest {

    /**
     * CMD
     * o  CONNECT X'01'
     * o  UDP ASSOCIATE X'03'
     */
    private int cmd;

    /**
     * ATYP address type of following address
     * o  IP V4 address: X'01'
     * o  DOMAINNAME: X'03'
     * o  IP V6 address: X'04'
     */
    private int atyp;

    /**
     * DST.ADDR desired destination address
     */
    private String dstAddr;

    /**
     * DST.PORT desired destination port in network octet order
     */
    private int dstPort;
}

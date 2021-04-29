package com.kdyzm.socks5.netty.decoder;


import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5Message;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequestDecoder;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @author kdyzm
 * @date 2021/4/28
 */
@Slf4j
public class TrojanResponseDecoder extends ReplayingDecoder<TrojanResponseDecoder.State> {


    enum State {
        /**
         *
         */
        INIT,
        SUCCESS,
        FAILURE
    }


    public TrojanResponseDecoder() {
        super(State.SUCCESS);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            switch (state()) {
                case INIT: {
                    //TODO 找不到trojan响应文档
//                    final int startOffset = in.readerIndex();
//                    final byte version = in.getByte(startOffset);
//                    if (version != 1) {
//                        throw new DecoderException("unsupported subnegotiation version: " + version + " (expected: 1)");
//                    }
//
//                    final int usernameLength = in.getUnsignedByte(startOffset + 1);
//                    final int passwordLength = in.getUnsignedByte(startOffset + 2 + usernameLength);
//                    final int totalLength = usernameLength + passwordLength + 3;
//
//                    in.skipBytes(totalLength);
//                    out.add(new DefaultSocks5PasswordAuthRequest(
//                            in.toString(startOffset + 2, usernameLength, CharsetUtil.US_ASCII),
//                            in.toString(startOffset + 3 + usernameLength, passwordLength, CharsetUtil.US_ASCII)));
//
//                    checkpoint(State.SUCCESS);
                }

                case FAILURE: {
                    in.skipBytes(actualReadableBytes());
                    break;
                }
                case SUCCESS:
                default: {
                    log.debug("trojan协议握手响应");
                    int readableBytes = actualReadableBytes();
                    if (readableBytes > 0) {
                        out.add(in.readRetainedSlice(readableBytes));
                    }
                    break;
                }
            }
        } catch (Exception e) {
            fail(out, e);
        }
    }


    private void fail(List<Object> out, Exception cause) {
        if (!(cause instanceof DecoderException)) {
            cause = new DecoderException(cause);
        }

        checkpoint(State.FAILURE);

        Socks5Message m = new DefaultSocks5PasswordAuthRequest("", "");
        m.setDecoderResult(DecoderResult.failure(cause));
        out.add(m);
    }


}

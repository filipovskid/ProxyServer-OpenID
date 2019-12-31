package com.filipovski.server.authentication;

import com.filipovski.server.utils.Utils;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;

public class AuthenticationHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {

    }

    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        FullHttpRequest request = (FullHttpRequest) msg;

        if(Utils.isAuthenticated("")) {
            ctx.pipeline().remove(this);
            ctx.fireChannelRead(request);
            return;
        }


    }
}

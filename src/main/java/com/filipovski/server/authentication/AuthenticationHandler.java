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
        ProxySession proxySession = (ProxySession) ctx.channel().attr(Utils.sessionAttributeKey).get();

        if(proxySession.isAuthenticated()) {
            ctx.pipeline().remove(this);
            ctx.fireChannelRead(request);
            return;
        }

        FullHttpResponse response =
                new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.FOUND);

        response.headers().set(HttpHeaderNames.LOCATION, "http://localhost:6555/login/login.html");
        HttpUtil.setContentLength(response, 0);

        ctx.writeAndFlush(response).addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                System.out.println(channelFuture.isSuccess());
            }
        });
    }
}

package com.filipovski.server.authentication;

import com.filipovski.server.utils.Utils;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

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

        response.headers().set(HttpHeaderNames.LOCATION, this.getRedirectUrl(request));
        HttpUtil.setContentLength(response, 0);

        ctx.writeAndFlush(response);
    }

    private String getRedirectUrl(FullHttpRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("target_url", request.uri());

        String redirectUrl = String.format("%s/login", Utils.basicUrl);

        try {
            redirectUrl = Utils.buildUrl(Utils.basicUrl, "/login", params).toString();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return redirectUrl;
    }
}

package com.filipovski.server.authentication;

import com.filipovski.server.models.ProxySession;
import com.filipovski.server.utils.Utils;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
//            ctx.pipeline().remove(this);
            ctx.fireChannelRead(request);
            return;
        }

        FullHttpResponse response =
                new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.TEMPORARY_REDIRECT);

        response.headers().set(HttpHeaderNames.LOCATION, this.getRedirectUrl(request));
        HttpUtil.setContentLength(response, 0);

        ctx.writeAndFlush(response);
    }

    private String getRedirectUrl(FullHttpRequest request) {
        Map<String, String> redirectParams = new HashMap<>();
//        String redirectUrl = String.format("%s/login", Utils.basicUrl);
        String redirectUrl = String.format("%s/auth", Utils.basicUrl);

        try {
//            Map<String, String> forwardParams = new HashMap<>();
//            forwardParams.put("target_url", request.uri());
//            forwardParams.put(Utils.proxySessionName, sessionId);

            String urlBase = request.headers().get(HttpHeaderNames.HOST);
            String target_url = URLEncoder.encode(request.uri(), StandardCharsets.UTF_8.name());

            String forwardUrl =
                    String.format("http://%s%s?target_url=%s", urlBase, Utils.foreignCaptiveEndpoint,
                            target_url);

            redirectParams.put("target_url", forwardUrl);

//            redirectUrl = Utils.buildUrl(Utils.basicUrl, "/login", redirectParams).toString();
            redirectUrl = Utils.buildUrl(Utils.basicUrl, "/auth", redirectParams).toString();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return redirectUrl;
    }
}

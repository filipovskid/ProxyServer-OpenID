package com.filipovski.server.utils;

import com.filipovski.server.authentication.ManagedHttpRequest;
import com.filipovski.server.authentication.OpenIDAuthHandler;
import com.filipovski.server.authentication.ProxySession;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

import java.io.File;
import java.net.HttpCookie;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class RouteManagerFactory {

    public static RouteManager fileRouteManager(String filePath) {
        return FileRouteManager.of(filePath);
    }

    public static RouteManager loginFileRouter(String filePath) {
        return FileRouteManager.of(filePath)
                .setParameterObtainer((ctx, queryParams) -> {
                    Map<String, String> fileParameters = new HashMap<>();

                    fileParameters.put("redirect-url", queryParams.get("target_url").get(0));

                    return fileParameters;
                });
    }

    public static RouteManager openidAuthManager() {
        return ((ctx, request, queryParams) -> {
            List<String> channelNames = ctx.pipeline().names();

            if(!channelNames.contains("openid-authenticator"))
                ctx.pipeline().addAfter(ctx.name(), "openid-authenticator", new OpenIDAuthHandler());

            ManagedHttpRequest managedRequest = ManagedHttpRequest.of(request, queryParams);
            ctx.fireChannelRead(managedRequest);
        });
    }

    public static RouteManager foreignRedirectManager() {
        return ((ctx, request, queryParams) -> {
            if(!queryParams.containsKey("target-url"))
                return; // Bad request handle

            String targetUrl = queryParams.get("target-url").get(0);
            FullHttpResponse response =
                    new DefaultFullHttpResponse(request.protocolVersion(),
                            HttpResponseStatus.TEMPORARY_REDIRECT);
            response.headers()
                    .set(HttpHeaderNames.LOCATION, targetUrl);
            HttpUtil.setContentLength(response, 0);
            setCookie(ctx, request, response);

            ctx.writeAndFlush(response);
            ReferenceCountUtil.release(request);
        });
    }

    private static void setCookie(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        ProxySession proxySession = (ProxySession) ctx.channel().attr(Utils.sessionAttributeKey).get();
        String sessionId = proxySession.getSessionId();
        String cookieString = request.headers().get(HttpHeaderNames.COOKIE, "_=");
        Map<String, HttpCookie> cookies = HttpCookie.parse(cookieString).stream()
                .collect(Collectors.toMap(HttpCookie::getName, Function.identity()));

        // Contains cookie
        if(!cookies.containsKey(Utils.proxySessionName) ||
                sessionId.equals(cookies.get(Utils.proxySessionName).getValue()))
            response.headers().set(HttpHeaderNames.SET_COOKIE, sessionId);
    }

    public static RouteManager foreignDefaultManager() {
        return ((ctx, request, queryParams) -> {
            ctx.fireChannelRead(ReferenceCountUtil.retain(request));
        });
    }
}

package com.filipovski.server.utils;

import com.filipovski.server.models.GUser;
import com.filipovski.server.models.ManagedHttpRequest;
import com.filipovski.server.authentication.OpenIDAuthHandler;
import com.filipovski.server.models.ProxySession;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

import java.io.UnsupportedEncodingException;
import java.net.HttpCookie;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                    if(!queryParams.containsKey("target_url"))
                        return fileParameters;

                    String redirectUrl = "";
                    String urlParameter = "";

                    try {
                        redirectUrl = URLEncoder.encode(queryParams.get("target_url").get(0),
                                StandardCharsets.UTF_8.name());
                        urlParameter =
                                URLEncoder.encode(String.format("url=%s", redirectUrl),
                                        StandardCharsets.UTF_8.name());
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }

                    fileParameters.put("redirect-url", urlParameter);

                    return fileParameters;
                });
    }

    public static RouteManager profileFileRouter(String filePath) {
        return FileRouteManager.of(filePath).setParameterObtainer((ctx, queryParams) -> {
            Map<String, String> fileParameters = new HashMap<>();
            ProxySession proxySession = ctx.channel().attr(Utils.sessionAttributeKey).get();

            if(!proxySession.isAuthenticated()) return fileParameters;

            GUser user = (GUser) proxySession.getAttribute("user");

            fileParameters.put("name", user.getName());
            fileParameters.put("email", user.getEmail());
            fileParameters.put("pictureUrl", user.getPicture());

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

    public static RouteManager authRedirectManager() {
        return ((ctx, request, queryParams) -> {
            ProxySession proxySession = (ProxySession) ctx.channel().attr(Utils.sessionAttributeKey).get();
            FullHttpResponse response =
                    new DefaultFullHttpResponse(request.protocolVersion(),
                            HttpResponseStatus.FOUND);
            HttpUtil.setContentLength(response, 0);
            String redirectUrl = "";

            try {
                if (queryParams.containsKey("target_url")) {
                    String targetUrl = queryParams.get("target_url").get(0);

                    if (proxySession.isAuthenticated()) {
                        redirectUrl = String.format("%s&%s=%s", targetUrl,
                                Utils.proxySessionName,
                                proxySession.getSessionId());
                    } else {
                        redirectUrl = Utils.buildUrl(Utils.basicUrl,
                                "/login",
                                flattenParams(queryParams)).toString();
                    }
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }

            setCookie(ctx, request, response);
            response.headers().set(HttpHeaderNames.LOCATION, redirectUrl);
            ctx.writeAndFlush(response);
        });
    }

    private static Map<String, String> flattenParams(Map<String, List<String>> queryParams) {
        return queryParams.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(), e-> e.getValue().get(0)));
    }

    public static RouteManager foreignRedirectManager() {
        return ((ctx, request, queryParams) -> {
            if(!queryParams.containsKey("target_url"))
                return; // Bad request handle

            String targetUrl = queryParams.get("target_url").get(0);
            FullHttpResponse response =
                    new DefaultFullHttpResponse(request.protocolVersion(),
                            HttpResponseStatus.FOUND);
            response.headers()
                    .set(HttpHeaderNames.LOCATION, targetUrl);
            HttpUtil.setContentLength(response, 0);
            setCookie(ctx, request, response);

            ctx.writeAndFlush(response);
//            ReferenceCountUtil.release(request);
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
                !sessionId.equals(cookies.get(Utils.proxySessionName).getValue())) {
            String cookieValue = String.format("%s=%s; path=/", Utils.proxySessionName, sessionId);
                    // request.headers().get(HttpHeaderNames.HOST));
            response.headers().set(HttpHeaderNames.SET_COOKIE, cookieValue);
        }
    }

    public static RouteManager foreignDefaultManager() {
        return ((ctx, request, queryParams) -> {
            ctx.fireChannelRead(ReferenceCountUtil.retain(request));
        });
    }

    public static RouteManager logoutManager() {
        return ((ctx, request, queryParams) -> {
            ProxySession proxySession = ctx.channel().attr(Utils.sessionAttributeKey).get();
            proxySession.invalidate(ctx);

            DefaultFullHttpResponse response =
                    new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.OK);
            HttpUtil.setContentLength(response, 0);

            ctx.writeAndFlush(response);
        });
    }
}

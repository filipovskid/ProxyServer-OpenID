package com.filipovski.server.utils;

import com.filipovski.server.authentication.ManagedHttpRequest;
import com.filipovski.server.authentication.OpenIDAuthHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

import java.io.File;
import java.util.List;

public final class RouteManagerFactory {

    public static RouteManager fileRouteManager(String filePath) {
        return FileRouteManager.of(filePath);
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

            ctx.writeAndFlush(response);
            ReferenceCountUtil.release(request);
        });
    }

    public static RouteManager foreignDefaultManager() {
        return ((ctx, request, queryParams) -> {
            ctx.fireChannelRead(ReferenceCountUtil.retain(request));
        });
    }
}

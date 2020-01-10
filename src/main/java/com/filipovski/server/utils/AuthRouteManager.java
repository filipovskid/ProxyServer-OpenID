package com.filipovski.server.utils;

import com.filipovski.server.authentication.ManagedHttpRequest;
import com.filipovski.server.authentication.OpenIDAuthHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class AuthRouteManager implements RouteManager {
//    public enum Type {
//        ACCESS,
//        AUTHENTICATE
//    }
//
//    private Type managerType;
//
//    public AuthRouteManager(Type type) {
//        this.managerType = type;
//    }

    public static AuthRouteManager of() {
        return new AuthRouteManager();
    }

    @Override
    public void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request,
                                  Map<String, List<String>> queryParams) throws IOException {
        // For adding another handler in which api calls are made. Check before adding it.
        List<String> channelNames = ctx.pipeline().names();

        if(!channelNames.contains("openid-authenticator"))
            ctx.pipeline().addAfter(ctx.name(), "openid-authenticator", new OpenIDAuthHandler());

        ManagedHttpRequest managedRequest = ManagedHttpRequest.of(request, queryParams);
        ctx.fireChannelRead(managedRequest);
    }


}

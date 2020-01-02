package com.filipovski.server.utils;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ForeignRouteManager implements RouteManager {
    @Override
    public void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request,
                                  Map<String, List<String>> queryParams) throws IOException {
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
    }
}

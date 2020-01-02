package com.filipovski.server.utils;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface RouteManager {
    void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request,
                           Map<String, List<String>> queryParams) throws IOException;
}

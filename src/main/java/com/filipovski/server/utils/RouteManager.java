package com.filipovski.server.utils;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

import java.io.IOException;

public interface RouteManager {
    void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) throws IOException;
}

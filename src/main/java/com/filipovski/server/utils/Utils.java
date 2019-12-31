package com.filipovski.server.utils;

import io.netty.util.AttributeKey;

public class Utils {
    public static final String proxySessionName = "poidSESSION";
    public static final AttributeKey sessionAttributeKey  = AttributeKey.valueOf("session");

    public static boolean notForLocalServer(String host) {
        return !host.contains("localhost");
    }

    public static boolean isAuthenticated(String name) {
        return true;
    }


}

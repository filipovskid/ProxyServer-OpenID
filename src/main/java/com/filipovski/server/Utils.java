package com.filipovski.server;

public class Utils {
    public static boolean notForLocalServer(String host) {
        return !host.contains("localhost");
    }

    public static boolean isAuthenticated(String name) {
        return true;
    }

}

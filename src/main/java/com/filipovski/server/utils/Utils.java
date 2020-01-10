package com.filipovski.server.utils;

import com.filipovski.server.models.ProxySession;
import io.netty.util.AttributeKey;
import org.apache.http.client.utils.URIBuilder;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

public class Utils {
    public static final AttributeKey<AppConfig> configAttributeKey = AttributeKey.valueOf("config");
    public static final String proxySessionName = "poidSESSION";
    public static final AttributeKey<ProxySession> sessionAttributeKey  = AttributeKey.valueOf("session");
    public static final String basicUrl = "http://localhost:6555";
    public static final String foreignCaptiveEndpoint = "/oid-proxy.oid/proxy";


    public static boolean notForLocalServer(String host) {
        return !host.contains("localhost");
    }

    public static boolean isAuthenticated(String name) {
        return true;
    }

    public static URL buildUrl(String base, String endpoint, Map<String, String> parameters) throws URISyntaxException, MalformedURLException {
        URIBuilder uriBuilder = new URIBuilder(base + endpoint);

        parameters.entrySet()
                .stream()
                .forEach(e -> uriBuilder.addParameter(e.getKey(), e.getValue()));

        return uriBuilder.build().toURL();
    }
}

package com.filipovski.server.authentication;

import io.netty.handler.codec.http.FullHttpRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManagedHttpRequest {
    private FullHttpRequest request;
    private Map<String, List<String>> queryParams;

    private ManagedHttpRequest(FullHttpRequest request) {
        this.request = request;
        this.queryParams = new HashMap<>();
    }

    public static ManagedHttpRequest of(FullHttpRequest request, Map<String, List<String>> params) {
        ManagedHttpRequest managedRequest = new ManagedHttpRequest(request);
        managedRequest.queryParams = params;

        return managedRequest;
    }

    public FullHttpRequest toFullHttpRequest() {
        return this.request;
    }

    public String getQueryParam(String param) {
        return queryParams.get(param).get(0);
    }
}

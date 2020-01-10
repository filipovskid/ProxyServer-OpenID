package com.filipovski.server.authentication;

import com.filipovski.server.models.GUser;
import com.filipovski.server.models.ManagedHttpRequest;
import com.filipovski.server.models.ProxySession;
import com.filipovski.server.utils.AppConfig;
import com.filipovski.server.utils.Utils;
import com.google.gson.Gson;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import org.apache.jena.ext.com.google.common.reflect.TypeToken;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.Response;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class OpenIDAuthHandler extends ChannelInboundHandlerAdapter {
    private AsyncHttpClient client = Dsl.asyncHttpClient();
    private Gson gson = new Gson();
    private Type type = new TypeToken<Map<String, String>>(){}.getType();
    private AppConfig config;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.config = ctx.channel().attr(Utils.configAttributeKey).get();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ManagedHttpRequest request = (ManagedHttpRequest) msg;

        this.exchangingAuthorizationCode(request)
                .execute().toCompletableFuture()
                .thenCompose(r -> this.gatherUserInformation(r).execute().toCompletableFuture())
                .thenAccept(r -> this.handleRequest(ctx, r, request))
                .exceptionally(t -> {
                    System.err.println(t);
                    return null;
                });
    }

    private BoundRequestBuilder exchangingAuthorizationCode(ManagedHttpRequest request) {
        BoundRequestBuilder requestBuilder = client.preparePost(config.getToken_endpoint())
                .addFormParam("code", request.getQueryParam("code"))
                .addFormParam("client_id", config.getClient_id())
                .addFormParam("client_secret", config.getClient_secret())
                .addFormParam("redirect_uri", config.getRedirect_uri())
                .addFormParam("grant_type", "authorization_code");

        return requestBuilder;
    }

    private BoundRequestBuilder gatherUserInformation(Response response) {
        Map<String, String> values = gson.fromJson(response.getResponseBody(), type);

        String authorizationHeader = String.format("%s %s",
                values.get("token_type"),
                values.get("access_token"));

        BoundRequestBuilder requestBuilder = client.prepareGet(config.getUserinfo_endpoint())
                .addQueryParam("scope", "openid email profile")
                .addHeader(HttpHeaderNames.AUTHORIZATION, authorizationHeader);

        return requestBuilder;
    }

    private void handleRequest(ChannelHandlerContext ctx, Response response, ManagedHttpRequest request) {
        this.authenticateUser(ctx, response);
        this.writeRedirectResponse(ctx, request);
    }

    private void authenticateUser(ChannelHandlerContext ctx, Response response) {
        ProxySession proxySession = ctx.channel().attr(Utils.sessionAttributeKey).get();
        Map<String, String> values = gson.fromJson(response.getResponseBody(), type);

        GUser user = GUser.of(values.get("name"), values.get("email"), values.get("picture"));

        proxySession.setAttribute("user", user)
                    .authenticate();
    }

    private void writeRedirectResponse(ChannelHandlerContext ctx, ManagedHttpRequest request) {
        ProxySession proxySession = (ProxySession) ctx.channel().attr(Utils.sessionAttributeKey).get();
        String state = request.getQueryParam("state");
        String target_url = this.getParameter(state, "url");
        String redirectUrl = "";

        try {
            redirectUrl = URLDecoder.decode(target_url, StandardCharsets.UTF_8.name());
            redirectUrl = String.format("%s&%s=%s", redirectUrl, Utils.proxySessionName, proxySession.getSessionId());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        FullHttpResponse response = new DefaultFullHttpResponse(request.toFullHttpRequest().protocolVersion(),
                                                                HttpResponseStatus.TEMPORARY_REDIRECT);
        response.headers().set(HttpHeaderNames.LOCATION, redirectUrl);
        HttpUtil.setContentLength(response, 0);

        ctx.writeAndFlush(response);
    }



    private String getParameter(String from, String param) {
        Map<String, String> params = Arrays.stream(from.split("&"))
                .map(i -> i.split("="))
                .collect(Collectors.toMap(a -> a[0], a -> a[1]));

        return params.get(param);
    }
}

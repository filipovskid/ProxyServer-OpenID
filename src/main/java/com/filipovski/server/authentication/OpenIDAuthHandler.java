package com.filipovski.server.authentication;

import com.filipovski.server.utils.Utils;
import com.google.gson.Gson;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.apache.jena.ext.com.google.common.reflect.TypeToken;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.Response;

import java.lang.reflect.Type;
import java.util.Map;

public class OpenIDAuthHandler extends ChannelInboundHandlerAdapter {
    private AsyncHttpClient client = Dsl.asyncHttpClient();
    private Gson gson = new Gson();
    private Type type = new TypeToken<Map<String, String>>(){}.getType();


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ManagedHttpRequest request = (ManagedHttpRequest) msg;

        this.exchangingAuthorizationCode(request)
                .execute().toCompletableFuture()
                .thenCompose(r -> this.gatherUserInformation(r).execute().toCompletableFuture())
                .thenAccept(r -> this.authenticateUser(ctx, r));
    }

    private BoundRequestBuilder exchangingAuthorizationCode(ManagedHttpRequest request) {
        BoundRequestBuilder requestBuilder = client.preparePost(String.format("https://oauth2.googleapis.com/token"))
                .addFormParam("code", request.getQueryParam("code"))
                .addFormParam("client_id", "506173786117-5mfv7vupsog2405vnkspg9in70gee0n1.apps.googleusercontent.com")
                .addFormParam("client_secret", "8yyOWTOmSLCMr9WirB8yQANE")
                .addFormParam("redirect_uri", "http://localhost:6555/code")
                .addFormParam("grant_type", "authorization_code");

        return requestBuilder;
    }

    private BoundRequestBuilder gatherUserInformation(Response response) {
        Map<String, String> values = gson.fromJson(response.getResponseBody(), type);

        String authorizationHeader = String.format("%s %s",
                values.get("token_type"),
                values.get("access_token"));

        BoundRequestBuilder requestBuilder = client.preparePost(String.format("https://openidconnect.googleapis.com/v1/userinfo"))
                .addQueryParam("scope", "openid email profile")
                .addHeader(HttpHeaderNames.AUTHORIZATION, authorizationHeader);

        return requestBuilder;
    }

    private void authenticateUser(ChannelHandlerContext ctx, Response response) {
        ProxySession proxySession = (ProxySession) ctx.channel().attr(Utils.sessionAttributeKey).get();
        Map<String, String> values = gson.fromJson(response.getResponseBody(), type);
        System.out.println(values);

        proxySession.setPicture(values.get("picture"))
                .setEmail(values.get("email"))
                .authenticate();
    }
}

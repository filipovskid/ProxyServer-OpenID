package com.filipovski.server.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.bind.JsonTreeReader;
import org.apache.jena.ext.com.google.common.reflect.TypeToken;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.Response;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class AppConfig {

    // Server config
    private int port;
    private String host;

    // OpenID settings
    private String client_id;
    private String client_secret;
    private String redirect_uri;

    // OpenID endpoints
    private String discovery_document;
    private String authorization_endpoint;
    private String token_endpoint;
    private String userinfo_endpoint;

    public void configureEndpoints() {
        AsyncHttpClient client = Dsl.asyncHttpClient();
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, String>>(){}.getType();

        try {
            Response response = client.prepareGet(this.discovery_document).execute().get();
            JsonObject values = JsonParser.parseString(response.getResponseBody()).getAsJsonObject();

            this.authorization_endpoint = values.get("authorization_endpoint").getAsString();
            this.token_endpoint = values.get("token_endpoint").getAsString();
            this.userinfo_endpoint = values.get("userinfo_endpoint").getAsString();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return;
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public String getClient_id() {
        return client_id;
    }

    public String getClient_secret() {
        return client_secret;
    }

    public String getRedirect_uri() {
        return redirect_uri;
    }

    public String getDiscovery_document() {
        return discovery_document;
    }

    public String getAuthorization_endpoint() {
        return authorization_endpoint;
    }

    public String getToken_endpoint() {
        return token_endpoint;
    }

    public String getUserinfo_endpoint() {
        return userinfo_endpoint;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setClient_id(String client_id) {
        this.client_id = client_id;
    }

    public void setClient_secret(String client_secret) {
        this.client_secret = client_secret;
    }

    public void setRedirect_uri(String redirect_uri) {
        this.redirect_uri = redirect_uri;
    }

    public void setDiscovery_document(String discovery_document) {
        this.discovery_document = discovery_document;
    }

    public void setAuthorization_endpoint(String authorization_endpoint) {
        this.authorization_endpoint = authorization_endpoint;
    }

    public void setToken_endpoint(String token_endpoint) {
        this.token_endpoint = token_endpoint;
    }

    public void setUserinfo_endpoint(String userinfo_endpoint) {
        this.userinfo_endpoint = userinfo_endpoint;
    }
}

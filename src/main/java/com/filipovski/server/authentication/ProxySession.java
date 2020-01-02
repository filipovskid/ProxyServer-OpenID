package com.filipovski.server.authentication;

import java.util.Map;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ProxySession {
    private Map<String, Object> attributes;
    private String sessionId;
    private boolean newSession;
    private boolean authenticated;
    private String pictureUrl;
    private String email;

    public ProxySession(String sessionId) {
        this.sessionId = sessionId;
        this.attributes = new ConcurrentHashMap<>();
        this.newSession = true;
        this.authenticated = false;
        this.pictureUrl = "https://i.stack.imgur.com/34AD2.jpg";
    }

    public static ProxySession of(String sessionId) {
        return new ProxySession(sessionId);
    }

    public Map<String, Object> getState() {
        return this.attributes;
    }

    public synchronized void resetState() {
        this.attributes = new ConcurrentHashMap<>();
    }

    public synchronized void setAttributes(String key, Object value) {
        this.attributes.putIfAbsent(key, value);
    }

    public synchronized boolean isNew() {
        return newSession;
    }

    public synchronized void notNew() {
        this.newSession = false;
    }

    public synchronized  boolean isAuthenticated() {
        return authenticated;
    }

    public ProxySession setPicture(String pictureUrl) {
        this.pictureUrl = pictureUrl;

        return this;
    }

    public ProxySession setEmail(String email) {
        this.email = email;

        return this;
    }

    public synchronized boolean isNewTerminated() {
        if(this.newSession) {
            this.newSession = false;

            return true;
        }

        return false;
    }

    public synchronized ProxySession authenticate() {
        this.authenticated = true;

        return this;
    }

    public synchronized void authenticate(String pictureUrl, String email) {
        this.pictureUrl = pictureUrl;
        this.email = email;
    }

    public String getSessionId() {
        return sessionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProxySession that = (ProxySession) o;
        return Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }
}

package com.filipovski.server.models;

import com.filipovski.server.utils.Utils;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ProxySession {
    private Map<String, ProxySession> sessionContainer;
    private Map<String, Object> attributes;
    private String sessionId;
    private boolean newSession;
    private boolean authenticated;

    public ProxySession(String sessionId) {
        this.sessionId = sessionId;
        this.attributes = new ConcurrentHashMap<>();
        this.newSession = true;
        this.authenticated = false;
    }

    public static ProxySession of(Map<String, ProxySession> sessionContainer, String sessionId) {
        ProxySession proxySession = new ProxySession(sessionId);
        proxySession.sessionContainer = sessionContainer;

        return proxySession;
    }

//    public static ProxySession of(String sessionId) {
//        return new ProxySession(sessionId);
//    }

    public Map<String, Object> getState() {
        return this.attributes;
    }

    public synchronized void resetState() {
        this.attributes = new ConcurrentHashMap<>();
    }

    public synchronized  void invalidate(ChannelHandlerContext ctx) {
        this.authenticated = false;
        this.sessionContainer.remove(this.getSessionId());
    }

    public synchronized ProxySession setAttribute(String key, Object value) {
        this.attributes.putIfAbsent(key, value);

        return this;
    }

    public synchronized Object getAttribute(String key) {
        return attributes.get(key);
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

    public String getSessionId() {
        return sessionId;
    }

    public boolean hasAttribute(String attribute) {
        return attribute.contains(attribute);
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
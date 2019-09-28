package com.filipovski.server;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.net.ssl.SSLException;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

public class TLSsHandler extends ChannelOutboundHandlerAdapter {

    private Connection connectionInfo;
    private Channel outboundChannel;
    private boolean client;

    private final List<Object> pendings;

    public TLSsHandler(Connection connectionInfo, Channel outboundChannel,
                      boolean client) {
        this.connectionInfo = connectionInfo;
        this.outboundChannel = outboundChannel;
        this.client = client;

        pendings = new ArrayList<>();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {

        if (connectionInfo.getServerAddress().getPort() == 443) {
            SslHandler sslHandler = sslCtx().newHandler(ctx.alloc());
            ctx.pipeline()
               .addBefore(ctx.name(), null, sslHandler)
               .addBefore(ctx.name(), null, new AlpnHandler(ctx));
        } else {
            configHttp1(ctx);
        }
    }

    private SslContext sslCtx() throws SSLException {
        if (client) {
        	SslContextBuilder builder = SslContextBuilder
                    .forClient()
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .applicationProtocolConfig(applicationProtocolConfig(false));
        	
        	builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
  
            return builder.build();
        }
        else {
            return SslContextBuilder
                    .forServer(new File("/Users/darko/hw_5_1/certs/server-chain.cert.pem"), 
                    		new File("/Users/darko/hw_5_1/private/server_pk8_nocrypt.key.pem"), null)
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .applicationProtocolConfig(applicationProtocolConfig(false))
                    .build();
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        flushPendings(ctx);
        ctx.flush();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        synchronized (pendings) {
            pendings.add(msg);
        }
        if (ctx.isRemoved()) {
            flushPendings(ctx);
            ctx.flush();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        outboundChannel.close();
        ctx.close();
    }

    private void flushPendings(ChannelHandlerContext ctx) {
        synchronized (pendings) {
            Iterator<Object> iterator = pendings.iterator();
            while (iterator.hasNext()) {
                ctx.write(iterator.next());
                iterator.remove();
            }
        }
    }

    private void configHttp1(ChannelHandlerContext ctx) {
        if (client) {
            ctx.pipeline().replace(this, null, new ProxyFrontendHandler(connectionInfo, outboundChannel));
        } else {
            ctx.pipeline().replace(this, null, new ProxyBackendHandler(connectionInfo, outboundChannel));
        }
    }

    private void configHttp2(ChannelHandlerContext ctx) {
        if (client) {
            ctx.pipeline().replace(this, null, new ProxyFrontendHandler(connectionInfo, outboundChannel));
        } else {
            ctx.pipeline().replace(this, null, new ProxyBackendHandler(connectionInfo, outboundChannel));
        }
    }
    
    private static ApplicationProtocolConfig applicationProtocolConfig(boolean http2) {
        if (http2) {
            return new ApplicationProtocolConfig(
                    Protocol.ALPN,
                    SelectorFailureBehavior.NO_ADVERTISE,
                    SelectedListenerFailureBehavior.ACCEPT,
                    ApplicationProtocolNames.HTTP_2,
                    ApplicationProtocolNames.HTTP_1_1);
        } else {
            return new ApplicationProtocolConfig(
                    Protocol.ALPN,
                    SelectorFailureBehavior.NO_ADVERTISE,
                    SelectedListenerFailureBehavior.ACCEPT,
                    ApplicationProtocolNames.HTTP_1_1);
        }
    }

    private class AlpnHandler extends ApplicationProtocolNegotiationHandler {
        private ChannelHandlerContext tlsCtx;

        private AlpnHandler(ChannelHandlerContext tlsCtx) {
            super(ApplicationProtocolNames.HTTP_1_1);
            this.tlsCtx = tlsCtx;
        }

        @Override
        protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
            if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                configHttp2(tlsCtx);
            } else if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
                configHttp1(tlsCtx);
            } else {
                throw new IllegalStateException("unknown protocol: " + protocol);
            }
        }
    }
}
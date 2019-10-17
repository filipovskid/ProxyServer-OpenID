package com.filipovski.server;

import java.io.File;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;

import javax.net.ssl.SSLException;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;


public class TlsHandler extends ChannelOutboundHandlerAdapter {
	
	private Connection connection;
	private Channel channel;
	boolean client;
	private final List<Object> pendings;
	
	public TlsHandler(Connection connection, Channel channel, boolean client) {
		this.connection = connection;
		this.channel = channel;
		this.client = client;
		
		pendings = new ArrayList<>();
	}
	
	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		if (connection.getServerAddress().getPort() == 443) {
			SslHandler sslHandler = sslCtx().newHandler(ctx.alloc());
			
			
			ctx.pipeline()
				.addBefore(ctx.name(), "sslHandler", sslHandler);
			configureHttpHandlers(ctx, 
					(context, handler) -> context.pipeline().replace(context.name(), null, handler));
		}
		else
			configureHttpHandlers(ctx,
					(context, handler) -> context.pipeline().replace(context.name(), null, handler));
	}
	
	private void configureHttpHandlers(ChannelHandlerContext ctx, 
			BiConsumer<ChannelHandlerContext, ChannelHandler> handlerConsumer) {
		if(client) {
			handlerConsumer.accept(ctx, new ProxyFrontendHandler(connection, channel));
		}
		else {
			handlerConsumer.accept(ctx, new ProxyBackendHandler(connection, channel));
		}
	}
	
	private SslContext sslCtx() throws SSLException, CertificateException {
		if(client)
			return ctxForServer();
		else
			return ctxForClient();
	}
	
	private static SslContext ctxForServer() throws SSLException, CertificateException {
		SelfSignedCertificate ssc = new SelfSignedCertificate();
        return SslContextBuilder
                .forServer( // ssc.certificate(), ssc.privateKey())
                		new File("/Users/darko/hw_5_1/certs/server-chain.cert.pem"), 
                		new File("/Users/darko/hw_5_1/private/server_pk8_nocrypt.key.pem"), null)
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        Protocol.ALPN,
                        SelectorFailureBehavior.NO_ADVERTISE,
                        SelectedListenerFailureBehavior.ACCEPT,
//                        ApplicationProtocolNames.HTTP_2,
                        ApplicationProtocolNames.HTTP_1_1))
                .build();
	}
	
	private static SslContext ctxForClient() throws SSLException {
        return SslContextBuilder.forClient()
        		.ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
        		.applicationProtocolConfig(new ApplicationProtocolConfig(
                        Protocol.ALPN,
                        SelectorFailureBehavior.NO_ADVERTISE,
                        SelectedListenerFailureBehavior.ACCEPT,
//                        ApplicationProtocolNames.HTTP_2,
                        ApplicationProtocolNames.HTTP_1_1))
//        		.trustManager(InsecureTrustManagerFactory.INSTANCE)
        		.build();
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
    	channel.close();
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
}

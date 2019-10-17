package com.filipovski.server;

import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//import java.util.regex.Matcher;
//import java.util.regex.Pattern;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;

public class ProxyFrontendHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	private static final Pattern URI_PATTERN = Pattern.compile("(https?)://([a-zA-Z0-9\\.\\-]+)(:(\\d+))?(/.*)");
    private static final Pattern TUNNEL_URI_PATTERN = Pattern.compile("^([a-zA-Z0-9\\.\\-_]+):(\\d+)");
    
	private Connection connection;
	private Channel outboundChannel;
	private HttpServerCodec httpServerCodec;
	private HttpObjectAggregator httpObjectAggregator;
	private boolean tunneled;

	public ProxyFrontendHandler(Connection connection, Channel outboundChannel) {
		this.connection = connection;
		this.outboundChannel = outboundChannel;
		tunneled = outboundChannel == null ? false : true;
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		httpServerCodec = new HttpServerCodec();
		httpObjectAggregator = new HttpObjectAggregator(1024 * 1024);
		ctx.pipeline()
			.addBefore(ctx.name(), null, httpServerCodec)
			.addBefore(ctx.name(), null, httpObjectAggregator);
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		ctx.pipeline()
			.remove(httpServerCodec)
			.remove(httpObjectAggregator);

		if (outboundChannel != null) {
			outboundChannel.close();
			outboundChannel = null;
		}
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
		if (!tunneled) {
			if (request.method() == HttpMethod.CONNECT) {
				handleTunnelRequest(ctx, request);
			} else {
				handleHttpRequest(ctx, request);
			}
        } else {
        	System.err.println("Tunneling: " + outboundChannel.pipeline());
        	System.out.println("REQ: " + request);
        	outboundChannel.writeAndFlush(request);
        }
	}

	private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) throws UnknownHostException {
		ResolvedUri resolvedUri = resolveHttpUri(request.uri());
		Address serverAddress = new Address(resolvedUri.hostname, resolvedUri.port);
		outboundChannel = createOutboundChannel(ctx, serverAddress).channel();
		
		FullHttpRequest newRequest = request.copy();
		newRequest.headers().set(request.headers());
		newRequest.setUri(request.uri());
		
		outboundChannel.writeAndFlush(newRequest);
	}
	
	private void handleTunnelRequest(ChannelHandlerContext ctx, FullHttpRequest request) throws UnknownHostException {
		ResolvedUri resolvedUri = resolveTunnelUri(request.uri());
		Address serverAddress = new Address(resolvedUri.hostname, resolvedUri.port);
		
		createOutboundChannel(ctx, serverAddress).addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) {
				if(future.isSuccess()) {
					FullHttpResponse response = 
							new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.OK);
					
					ctx.writeAndFlush(response);
					
					Connection newConnection = new Connection(connection.getClientAddress(), serverAddress);
                    ctx.pipeline().replace(ProxyFrontendHandler.this, null, 
                    		new TlsHandler(newConnection, future.channel(), true));
                    System.out.println("ProxyFrontendHandler.this " + ProxyFrontendHandler.this);
                    System.out.println("Frontend handler pipeline after tunnel: " + ctx.pipeline());
				}
			}
		});
	}
	
	private ResolvedUri resolveHttpUri(String uri) throws UnknownHostException {
		Matcher matcher = URI_PATTERN.matcher(uri);
		
		if(matcher.find()) {
			String scheme = matcher.group(1);
	        String hostname = matcher.group(2);
	        int port = resolvePort(scheme, matcher.group(4));
	        String path = matcher.group(5);
	        return new ResolvedUri(scheme, hostname, port, path);
		}
		else {
			throw new UnknownHostException("Could not resolve the http uri " + uri);		
		}
	}
	
	private ResolvedUri resolveTunnelUri(String uri) throws UnknownHostException {
		 Matcher matcher = TUNNEL_URI_PATTERN.matcher(uri);
	        if (matcher.find()) {
	        	String hostname = matcher.group(1);
	        	int port = Integer.parseInt(matcher.group(2));
	            return new ResolvedUri(hostname, port);
	        } else {
	            throw new UnknownHostException("Could not resolve the tunnel uri " + uri);
	        }
	}
	
	private int resolvePort(String scheme,String port) {
		if(port == null || port.isEmpty()) { 
			return scheme.equals("https") ? 443 : 80;
		}
		
		return Integer.parseInt(port);
	}
	
	private ChannelFuture createOutboundChannel(ChannelHandlerContext ctx, Address serverAddress) {
		connection = new Connection(connection.getClientAddress(), 
									new Address(serverAddress.getHost(), 
												serverAddress.getPort()));
		
		Bootstrap bootstrap = new Bootstrap();
		ChannelFuture future = bootstrap.group(ctx.channel()
				.eventLoop())
				.channel(ctx.channel().getClass())
				.handler(new ChannelInitializer<Channel>() {
					
		            @Override
		            protected void initChannel(Channel ch) throws Exception {
		                ch.pipeline().addLast(new TlsHandler(connection, ctx.channel(), false));
		            }
		        })
				.connect(serverAddress.getHost(), serverAddress.getPort());
		
		future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                	System.out.println("Channel closed - opc");
                    ctx.channel().close();
                }
            }
        });
		
		return future;
	}
	
	private class ResolvedUri {
		private String scheme;
		private String hostname;
		private int port;
		private String path;
		
		public ResolvedUri(String scheme, String hostname, int port, String path) {
			this.scheme = scheme;
			this.hostname = hostname;
			this.port = port;
			this.path = path;
		}
		
		public ResolvedUri(String hostname, int port) {
			this.hostname = hostname;
			this.port = port;
		}
		
	}
}

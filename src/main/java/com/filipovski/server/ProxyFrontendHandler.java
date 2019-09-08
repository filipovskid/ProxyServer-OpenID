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
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

public class ProxyFrontendHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	private static final Pattern URI_PATTERN = Pattern.compile("(https?)://([a-zA-Z0-9\\.\\-]+)(:(\\d+))?(/.*)");
	
	private Connection connection;
	private Channel outboundChannel;
	private HttpServerCodec httpServerCodec;
	private HttpObjectAggregator httpObjectAggregator;

	public ProxyFrontendHandler(Connection connection, Channel outboundChannel) {
		this.connection = connection;
		this.outboundChannel = outboundChannel;
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
		if (request.method() == HttpMethod.CONNECT) {
			System.out.println("CONNECT");
			return;
		} else {
			System.out.println("HTTPS");
			handleHttpRequest(ctx, request);
		}
	}

	private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) throws UnknownHostException {
		ResolvedUri resolvedUri = resolveHttpUri(request.uri());
		Address serverAddress = new Address(resolvedUri.hostname, resolvedUri.port);
		outboundChannel = createOutboundChannel(ctx, serverAddress);
		
		FullHttpRequest newRequest = request.copy();
		newRequest.headers().set(request.headers());
		newRequest.setUri(request.uri());

		System.out.println("About to call write");
		outboundChannel.writeAndFlush(newRequest);
		System.out.println("Channel active " + outboundChannel.isActive());
		System.out.println("Frontend written");
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
			throw new UnknownHostException("Could not resolve the http uri !!");		
		}
	}
	
	private int resolvePort(String scheme,String port) {
		if(port == null || port.isEmpty()) { 
			return scheme.equals("https") ? 443 : 80;
		}
		
		return Integer.parseInt(port);
	}
	
	private Channel createOutboundChannel(ChannelHandlerContext ctx, Address serverAddress) {
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
		                ch.pipeline().addLast(new ProxyBackendHandler(connection, ctx.channel()));
		            }
		        })
				.connect(serverAddress.getHost(), serverAddress.getPort());
		System.out.print("Server addr:" + serverAddress.getHost());
		
		future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    ctx.channel().close();
                }
            }
        });
		
		return future.channel();
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
		
		
	}
}

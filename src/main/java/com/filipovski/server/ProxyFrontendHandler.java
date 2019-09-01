package com.filipovski.server;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

public class ProxyFrontendHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	private static final Pattern PATH_PATTERN = Pattern.compile("(https?)://([a-zA-Z0-9\\.\\-]+)(:(\\d+))?(/.*)");
	private static final Pattern TUNNEL_ADDR_PATTERN = Pattern.compile("^([a-zA-Z0-9\\.\\-_]+):(\\d+)");

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
		ctx.pipeline().addBefore(ctx.name(), null, httpServerCodec).addBefore(ctx.name(), null, httpObjectAggregator);
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		ctx.pipeline().remove(httpServerCodec).remove(httpObjectAggregator);

		if (outboundChannel != null) {
			outboundChannel.close();
			outboundChannel = null;
		}
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
		System.out.println("Frontend read");
		if (request.method() == HttpMethod.CONNECT) {
			return;
		} else {
			handleHttpRequest(ctx, request);
		}
	}

	private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
		FullPath fullPath = resolveHttpProxyPath(request.uri());
		Address serverAddress = new Address(fullPath.host, fullPath.port);

		if (outboundChannel != null && !connection.getServerAddress().equals(serverAddress)) {
			outboundChannel.close();
			outboundChannel = null;
		}
		if (outboundChannel != null && !outboundChannel.isActive()) {
			outboundChannel.close();
			outboundChannel = null;
		}

		if (outboundChannel == null) {
			outboundChannel = createOutboundChannel(ctx, serverAddress);
		}

		FullHttpRequest newRequest = request.copy();
		newRequest.headers().set(request.headers());
		newRequest.setUri(fullPath.path);

		outboundChannel.writeAndFlush(newRequest);
		System.out.println("Channel active " + outboundChannel.isActive());
		System.out.println("Frontend written");
	}

	private FullPath resolveHttpProxyPath(String fullPath) {
		Matcher matcher = PATH_PATTERN.matcher(fullPath);
		if (matcher.find()) {
			String scheme = matcher.group(1);
			String host = matcher.group(2);
			int port = resolvePort(scheme, matcher.group(4));
			String path = matcher.group(5);
			return new FullPath(scheme, host, port, path);
		} else {
			throw new IllegalStateException("Illegal http proxy path: " + fullPath);
		}
	}

	private int resolvePort(String scheme, String port) {
		if (port == null || port.isEmpty()) {
			return "https".equals(scheme) ? 443 : 80;
		}
		return Integer.parseInt(port);
	}

	private Channel createOutboundChannel(ChannelHandlerContext ctx, Address serverAddress) {
		connection = new Connection(connection.getClientAddress(), new Address(serverAddress.getHost(), serverAddress.getPort()));
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
				.connect("50.62.232.1", 80);

//		future.addListener(ChannelFutureListener.CLOSE);
		System.out.println("ACTIVE: " + future.channel().isActive());
		
		return future.channel();
	}

	private static class FullPath {
		private String scheme;
		private String host;
		private int port;
		private String path;

		private FullPath(String scheme, String host, int port, String path) {
			this.scheme = scheme;
			this.host = host;
			this.port = port;
			this.path = path;
		}

		@Override
		public String toString() {
			return "FullPath{" + "scheme='" + scheme + '\'' + ", host='" + host + '\'' + ", port=" + port + ", path='"
					+ path + '\'' + '}';
		}
	}
}

package com.filipovski.server;

import java.io.File;
import java.net.InetSocketAddress;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.router.Router;
import io.netty.handler.stream.ChunkedWriteHandler;


public class ProxyServerInitializer extends ChannelInitializer<SocketChannel> {

	@Override
	protected void initChannel(SocketChannel ch) throws Exception {
		InetSocketAddress remoteAddress = ch.remoteAddress();
		Connection connection = new Connection(new Address(remoteAddress.getHostString(), remoteAddress.getPort()));
		String base = "/Users/darko/Documents/Projects/ProxyServer-OpenID/";
		
		System.out.println("Initialize");
		Router<File> router = new Router<File>()
				.GET("login.html", new File(base + "login/login.html"))
				.GET("login_darko", new File(base + "login/google_login.html"))
	            .notFound(new File(base + "login/bad.html"));
		
		ch.pipeline()
//			.addLast(new HttpServerCodec())
//			.addLast(new HttpObjectAggregator(1024 * 1024))
//			.addLast(new ProxyFrontendHandler(connection, null));
//			.addLast(new AuthenticationHandler());
//			.addLast(new ChunkedWriteHandler())
			.addLast("route-handler", new HttpRouteHandler(connection, router))
			.addLast("frontend-handler", new ProxyFrontendHandler(connection, null));
	}
	
}

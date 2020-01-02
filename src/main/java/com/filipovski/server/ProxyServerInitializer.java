package com.filipovski.server;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.Map;

import com.filipovski.server.authentication.AuthenticationHandler;
import com.filipovski.server.authentication.ProxySession;
import com.filipovski.server.utils.AuthRouteManager;
import com.filipovski.server.utils.FileRouteManager;
import com.filipovski.server.utils.RouteManager;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.router.Router;
import io.netty.util.AttributeKey;


public class ProxyServerInitializer extends ChannelInitializer<SocketChannel> {
	Map<String, ProxySession> sessionContainer;

	public ProxyServerInitializer(Map<String, ProxySession> sessionContainer) {
		this.sessionContainer = sessionContainer;
	}

	@Override
	protected void initChannel(SocketChannel ch) throws Exception {
		InetSocketAddress remoteAddress = ch.remoteAddress();
		Connection connection = new Connection(new Address(remoteAddress.getHostString(), remoteAddress.getPort()));
		String base = "/Users/darko/Documents/Projects/ProxyServer-OpenID/";
		
		System.out.println("Initialize");

		Router<RouteManager> router = new Router<RouteManager>()
				.GET("/login", FileRouteManager.of("static/single_login.html"))
				.GET("/code", new AuthRouteManager());
//				.notFound(FileRouteManager.of("/static/bad.html"));

		ch.attr(AttributeKey.valueOf("session-container")).set(this.sessionContainer);

		ch.pipeline()
//			.addLast(new HttpServerCodec())
//			.addLast(new HttpObjectAggregator(1024 * 1024))
//			.addLast(new ProxyFrontendHandler(connection, null));
//			.addLast(new AuthenticationHandler());
//			.addLast(new ChunkedWriteHandler())
			.addLast("route-handler", new HttpRouteHandler(connection, router))
            .addLast("authentication-handler", new AuthenticationHandler())
			.addLast("frontend-handler", new ProxyFrontendHandler(connection, null));
	}
	
}

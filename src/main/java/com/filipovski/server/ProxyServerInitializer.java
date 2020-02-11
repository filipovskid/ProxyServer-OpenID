package com.filipovski.server;

import java.net.InetSocketAddress;
import java.util.Map;

import com.filipovski.server.authentication.AuthenticationHandler;
import com.filipovski.server.models.ProxySession;
import com.filipovski.server.utils.*;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.router.Router;
import io.netty.util.AttributeKey;


public class ProxyServerInitializer extends ChannelInitializer<SocketChannel> {
	Map<String, ProxySession> sessionContainer;
	AppConfig config;

	public ProxyServerInitializer(Map<String, ProxySession> sessionContainer, AppConfig config) {
		this.sessionContainer = sessionContainer;
		this.config = config;
	}

	@Override
	protected void initChannel(SocketChannel ch) throws Exception {
		InetSocketAddress remoteAddress = ch.remoteAddress();
		Connection connection = new Connection(new Address(remoteAddress.getHostString(), remoteAddress.getPort()));

		Router<RouteManager> localRouter = new Router<RouteManager>()
				.GET("/login", RouteManagerFactory.loginFileRouter("static/single_login.html"))
				.GET("/code", RouteManagerFactory.openidAuthManager())
				.GET("/auth", RouteManagerFactory.authRedirectManager())
				.GET("/profile", RouteManagerFactory.profileFileRouter("static/profile.html"))
				.GET("/logout", RouteManagerFactory.logoutManager());
//				.notFound(FileRouteManager.of("/static/bad.html"));

		Router<RouteManager> foreignRouter = new Router<RouteManager>()
				.GET(Utils.foreignCaptiveEndpoint, RouteManagerFactory.foreignRedirectManager())
				.notFound(RouteManagerFactory.foreignDefaultManager());

		ch.attr(Utils.sessionContainerAttributeKey).set(this.sessionContainer);
		ch.attr(Utils.configAttributeKey).set(this.config);

		ch.pipeline()
			.addLast("route-handler", new HttpRouteHandler(connection, localRouter, foreignRouter))
            .addLast("authentication-handler", new AuthenticationHandler())
			.addLast("frontend-handler", new ProxyFrontendHandler(connection, null));
	}
	
}

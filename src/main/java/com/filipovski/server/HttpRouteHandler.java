package com.filipovski.server;

import com.filipovski.server.authentication.ProxySession;
import com.filipovski.server.utils.RouteManager;
import com.filipovski.server.utils.Utils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.router.RouteResult;
import io.netty.handler.codec.http.router.Router;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;

import java.net.HttpCookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HttpRouteHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	private Connection connection;
	private HttpServerCodec httpServerCodec;
	private HttpObjectAggregator httpObjectAggregator;
	private ChunkedWriteHandler chunkedWriteHandler;
	private final Router<RouteManager> localRouter;
	private final Router<RouteManager> foreignRouter;

	public HttpRouteHandler(Connection connection, Router<RouteManager> localRouter,
							Router<RouteManager> foreignRouter) {
		this.connection = connection;
		this.localRouter = localRouter;
		this.foreignRouter = foreignRouter;
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		httpServerCodec = new HttpServerCodec();
		httpObjectAggregator = new HttpObjectAggregator(1024 * 1024);
		chunkedWriteHandler = new ChunkedWriteHandler();
		
		ctx.pipeline()
			.addBefore(ctx.name(), null, httpServerCodec)
			.addBefore(ctx.name(), null, httpObjectAggregator)
			.addBefore(ctx.name(), null, chunkedWriteHandler);
	}

	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		ctx.pipeline()
			.remove(httpServerCodec)
			.remove(httpObjectAggregator)
			.remove(chunkedWriteHandler);
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
		String host = request.headers().get(HttpHeaderNames.HOST);


		if(Utils.notForLocalServer(host)) {
			ctx.fireChannelRead(ReferenceCountUtil.retain(request));

//			attachSession(ctx, request, routeResult.queryParams());
			return;
		}

		RouteResult<RouteManager> routeResult = localRouter.route(request.method(), request.uri());
		RouteManager routeManager = routeResult.target();
		attachSession(ctx, request, routeResult.queryParams());

		routeManager.handleHttpRequest(ctx, request, routeResult.queryParams());
	}

	private ProxySession attachSession(ChannelHandlerContext ctx, FullHttpRequest request,
									   Map<String, List<String>> queryParams) {
		if(ctx.channel().hasAttr(Utils.sessionAttributeKey))
			return (ProxySession) ctx.channel().attr(Utils.sessionAttributeKey).get();

		Map<String, ProxySession> sessionContainer = (Map<String, ProxySession>) ctx.channel()
				.attr(AttributeKey.valueOf("session-container")).get();
		String cookieString = request.headers().get(HttpHeaderNames.COOKIE, "_=");

		// Obtain old session or create a new one
		ProxySession proxySession = getProxySession(sessionContainer, cookieString, queryParams);

		sessionContainer.putIfAbsent(proxySession.getSessionId(), proxySession);
		ctx.channel().attr(Utils.sessionAttributeKey).set(proxySession);

		return proxySession;
	}

	private ProxySession getProxySession(Map<String, ProxySession> sessionContainer, String cookieString,
										 Map<String, List<String>> queryParams) {
		Map<String, HttpCookie> cookies = HttpCookie.parse(cookieString).stream()
				.collect(Collectors.toMap(HttpCookie::getName, Function.identity()));

		// Contains cookie
		if(cookies.containsKey(Utils.proxySessionName) &&
				sessionContainer.containsKey(cookies.get(Utils.proxySessionName).getValue())) {
			String sessionCookie = cookies.get(Utils.proxySessionName).getValue();

			return sessionContainer.get(sessionCookie);
		}

		// Contains query string
		if(queryParams.containsKey(Utils.proxySessionName) &&
				sessionContainer.containsKey(cookies.get(Utils.proxySessionName).getValue())) {
			String sessionParam = queryParams.get(Utils.proxySessionName).get(0);

			return sessionContainer.get(sessionParam);
		}

		// Creater new session
		String sessionId = UUID.randomUUID().toString();
		ProxySession proxySession = ProxySession.of(sessionId);

		return proxySession;
	}
}

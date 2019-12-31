package com.filipovski.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.activation.MimetypesFileTypeMap;
import javax.rmi.CORBA.Util;

import com.filipovski.server.authentication.ProxySession;
import com.filipovski.server.utils.Utils;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.codec.http.router.RouteResult;
import io.netty.handler.codec.http.router.Router;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;

public class HttpRouteHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	private Connection connection;
	private HttpServerCodec httpServerCodec;
	private HttpObjectAggregator httpObjectAggregator;
	private ChunkedWriteHandler chunkedWriteHandler;
	private final Router<File> router;

	public HttpRouteHandler(Connection connection, Router<File> router) {
		this.connection = connection;
		this.router = router;
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
		attachSession(ctx, request);

		if(Utils.notForLocalServer(host)) {
//			detachRouteHandler(ctx);
			ctx.fireChannelRead(ReferenceCountUtil.retain(request));

			return;
		}

		RouteResult<File> routeResult = router.route(request.method(), request.uri());

//		prepareLoginHtmlFile(routeResult.target());
		sendFileResponse(ctx, request, routeResult.target());
	}

	private ProxySession attachSession(ChannelHandlerContext ctx, FullHttpRequest request) {
		String cookieString = request.headers().get(HttpHeaderNames.COOKIE, "_=");
		Map<String, ProxySession> sessionContainer = (Map<String, ProxySession>) ctx.channel()
				.attr(AttributeKey.valueOf("session-container")).get();

		ProxySession proxySession = getProxySession(sessionContainer, cookieString);

		sessionContainer.putIfAbsent(proxySession.getSessionId(), proxySession);
		ctx.channel().attr(Utils.sessionAttributeKey).set(proxySession);

		return proxySession;
	}

	private ProxySession getProxySession(Map<String, ProxySession> sessionContainer, String cookieString) {
		Map<String, HttpCookie> cookies = HttpCookie.parse(cookieString).stream()
				.collect(Collectors.toMap(HttpCookie::getName, Function.identity()));

		if(cookies.containsKey(Utils.proxySessionName) &&
				sessionContainer.containsKey(cookies.get(Utils.proxySessionName).getValue())) {
			String sessionCookie = cookies.get(Utils.proxySessionName).getValue();

			return sessionContainer.get(sessionCookie);
		}

		String sessionId = UUID.randomUUID().toString();
		ProxySession proxySession = ProxySession.of(sessionId);

		return proxySession;
	}

	private void detachRouteHandler(ChannelHandlerContext ctx) {
		ctx.pipeline()
				.remove(httpObjectAggregator)
				.remove(httpServerCodec)
				.remove(chunkedWriteHandler)
				.replace(HttpRouteHandler.this, null, new ProxyFrontendHandler(this.connection, null));
	}

	private void sendFileResponse(ChannelHandlerContext ctx, FullHttpRequest request, File file) throws IOException {
		RandomAccessFile raf;
		ProxySession proxySession = (ProxySession) ctx.channel().attr(Utils.sessionAttributeKey).get();

		try {
			raf = new RandomAccessFile(file, "r");
		} catch (FileNotFoundException ignore) {
//		            sendError(ctx, NOT_FOUND);
			return;
		}

		long fileLength = raf.length();

		HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

		// Attach session cookie
		if(proxySession.isNewTerminated()) {
			String cookieString = String.format("%s=%s; ", Utils.proxySessionName, proxySession.getSessionId());
			response.headers().set(HttpHeaderNames.SET_COOKIE, cookieString);
		}

		HttpUtil.setContentLength(response, fileLength);
		setContentTypeHeader(response, file);

		if (HttpUtil.isKeepAlive(request)) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		}

		// Write the initial line and the header.
		ctx.write(response);

		// Write the content.
		ChannelFuture sendFileFuture;
		ChannelFuture lastContentFuture;
		if (ctx.pipeline().get(SslHandler.class) == null) {
			sendFileFuture = ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength),
					ctx.newProgressivePromise());
			// Write the end marker.
			lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
		} else {
			sendFileFuture = ctx.writeAndFlush(new HttpChunkedInput(new ChunkedFile(raf, 0, fileLength, 8192)),
					ctx.newProgressivePromise());
			// HttpChunkedInput will write the end marker (LastHttpContent) for us.
			lastContentFuture = sendFileFuture;
		}

		sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
			@Override
			public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
				if (total < 0) { // total unknown
					System.err.println(future.channel() + " Transfer progress: " + progress);
				} else {
					System.err.println(future.channel() + " Transfer progress: " + progress + " / " + total);
				}
			}

			@Override
			public void operationComplete(ChannelProgressiveFuture future) {
				System.err.println(future.channel() + " Transfer complete.");
			}
		});

		// Decide whether to close the connection or not.
		if (!HttpUtil.isKeepAlive(request)) {
			// Close the connection when the whole content is written out.
			lastContentFuture.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture future) {
					ctx.close();
				}
			}); // ChannelFutureListener.CLOSE);
		}
	}
	
	private static void setContentTypeHeader(HttpResponse response, File file) {
		MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
	}
	
	private void prepareLoginHtmlFile(File file) throws URISyntaxException, IOException {
		Charset charset = StandardCharsets.UTF_8;
		String content = new String(Files.readAllBytes(file.toPath()), charset);
		content = content.replaceAll("@\\{LOGIN-URL\\}", getLoginUrl());
		Files.write(file.toPath(), content.getBytes(charset));	
	}

	private String getLoginUrl() throws MalformedURLException, URISyntaxException {
		Map<String, String> parameters = new HashMap<>();
		String loginBaseUrl = "https://accounts.google.com/";
		String clientID = "506173786117-5mfv7vupsog2405vnkspg9in70gee0n1.apps.googleusercontent.com";

		parameters.put("client_id", clientID);
		parameters.put("response_type", "code");
		parameters.put("scope", "openid email");
		parameters.put("redirect_uri", "http://localhost:6665/login_darko");
		parameters.put("state", "darko_filipovski");
		parameters.put("nonce", "123OpenID");

		return RequestUtils.buildUrl(loginBaseUrl, "o/oauth2/v2/auth", parameters).toString();
	}
}

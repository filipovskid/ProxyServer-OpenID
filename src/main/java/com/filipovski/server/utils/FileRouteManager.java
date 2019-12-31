package com.filipovski.server.utils;

import com.filipovski.server.RequestUtils;
import com.filipovski.server.authentication.ProxySession;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class FileRouteManager implements RouteManager {
    private File file;
    private URL resourcePath;

    public FileRouteManager(String filePath) {
        this.resourcePath = FileRouteManager.class.getClassLoader().getResource(filePath);
        String tst = this.resourcePath.getPath();
        this.file = new File(tst);
    }

    public static FileRouteManager of(String filePath) {
        FileRouteManager manager = new FileRouteManager(filePath);

        return manager;
    }

    @Override
    public void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) throws IOException {
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

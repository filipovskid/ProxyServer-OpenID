package com.filipovski.server;

import com.filipovski.server.authentication.ProxySession;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyServer {
	public static void main(String args[]) throws InterruptedException {
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		EventLoopGroup bossGroup = new NioEventLoopGroup();
		Map<String, ProxySession> sessionContainer = new ConcurrentHashMap<>();

		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup)
				.channel(NioServerSocketChannel.class)
				.childHandler(new ProxyServerInitializer(sessionContainer));
			
			// Bind and start to accept incoming connections.
            ChannelFuture f = b.bind("localhost", 6555).sync(); // (7)
    
            // Wait until the server socket is closed.
            // In this example, this does not happen, but you can do that to gracefully
            // shut down your server.
            f.channel().closeFuture().sync();
		} finally {
			workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
		}
	}
}

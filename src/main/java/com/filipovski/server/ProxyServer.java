package com.filipovski.server;

import com.filipovski.server.models.ProxySession;
import com.filipovski.server.utils.AppConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyServer {

	private static AppConfig loadConfig() {
		Yaml yaml = new Yaml(new Constructor(AppConfig.class));
		InputStream inputStream = ProxyServer.class
				.getClassLoader()
				.getResourceAsStream("config.yml");
		AppConfig config = yaml.load(inputStream);

		return config;
	}

	public static void main(String args[]) throws InterruptedException {
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		EventLoopGroup bossGroup = new NioEventLoopGroup();
		Map<String, ProxySession> sessionContainer = new ConcurrentHashMap<>();
		AppConfig config = loadConfig();
		config.configureEndpoints();

		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup)
				.channel(NioServerSocketChannel.class)
				.childHandler(new ProxyServerInitializer(sessionContainer, config));
			
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

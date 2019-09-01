package com.filipovski.server;

import java.net.InetSocketAddress;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;


public class ProxyServerInitializer extends ChannelInitializer<SocketChannel> {

	@Override
	protected void initChannel(SocketChannel ch) throws Exception {
		InetSocketAddress remoteAddress = ch.remoteAddress();
		Connection connection = new Connection(new Address(remoteAddress.getHostString(), remoteAddress.getPort()));
		
		ch.pipeline()
//			.addLast(new HttpServerCodec())
//			.addLast(new HttpObjectAggregator(1024 * 1024))
			.addLast(new ProxyFrontendHandler(connection, null));
	}
	
}

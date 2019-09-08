package com.filipovski.server;


import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.PendingWriteQueue;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ProxyBackendHandler extends SimpleChannelInboundHandler<HttpObject> {
	private Connection connection;
    private Channel inboundChannel;
    private RequestOutboundHandler requestOutboundHandler;
    private int actionNum;
    
    private volatile HttpRequest currentRequest;

    public ProxyBackendHandler(Connection connection, Channel inboundChannel) {
        this.connection = connection;
        this.inboundChannel = inboundChannel;
        this.requestOutboundHandler = new RequestOutboundHandler();
        
        this.actionNum = 0;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
    	actionNum++;
    	System.out.println("channelActive " + actionNum);
        requestOutboundHandler.tryWritingRequests("ActiveOut");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    	actionNum++;
    	System.out.println("channelInactive " + actionNum);
//        delayOutboundHandler.release();
//        outboundChannel.pipeline().fireUserEventTriggered(new OutboundChannelClosedEvent(connectionInfo, false));
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    	actionNum++;
    	System.out.println("handlerAdded " + actionNum);
        ctx.pipeline()
           .addBefore(ctx.name(), null, new HttpClientCodec())
           .addBefore(ctx.name(), null, this.requestOutboundHandler);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, HttpObject httpObject)
            throws Exception {
    	actionNum++;
    	System.out.println("channelRead " + actionNum);

    	inboundChannel.writeAndFlush(ReferenceCountUtil.retain(httpObject));

//        if (httpObject instanceof HttpResponse) {
//            currentRequest = null;
//            delayOutboundHandler.next();
//        }
    }

    public class RequestOutboundHandler extends ChannelOutboundHandlerAdapter {
    	private PendingWriteQueue requestsQueue;
    	private ChannelHandlerContext handlerContext;
    	
    	public synchronized void tryWritingRequests(String where) {
    		System.out.println("	Try writing " + where);

    		if(!handlerContext.channel().isActive() || this.requestsQueue.isEmpty()) {
    			System.out.println("RETURN");
    			return;
    		}

    		if(this.requestsQueue.removeAndWriteAll() != null)
    			handlerContext.flush();
    	}
    	
    	@Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        	actionNum++;
        	System.out.println("D: handlerAdded " + actionNum);
            handlerContext = ctx.pipeline().context(this);
    		requestsQueue = new PendingWriteQueue(ctx);
        }
    	
    	@Override
		public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        	actionNum++;
        	System.out.println("D: write " + actionNum);
        	
    		if(msg instanceof FullHttpRequest) {
    			HttpRequest request = (HttpRequest) msg;
    			System.out.println("D: \n" + request);
    			requestsQueue.add(request, promise);
    			tryWritingRequests("In");
    		}
    	}
    }
}
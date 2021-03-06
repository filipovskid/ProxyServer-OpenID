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
import io.netty.util.ReferenceCountUtil;


public class ProxyBackendHandler extends SimpleChannelInboundHandler<HttpObject> {
	private Connection connection;
    private Channel inboundChannel;
    private RequestOutboundHandler requestOutboundHandler;

    private volatile HttpRequest currentRequest;

    public ProxyBackendHandler(Connection connection, Channel inboundChannel) {
        this.connection = connection;
        this.inboundChannel = inboundChannel;
        this.requestOutboundHandler = new RequestOutboundHandler();
        
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        requestOutboundHandler.tryWritingRequests();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        ctx.pipeline()
           .addBefore(ctx.name(), null, new HttpClientCodec())
           .addBefore(ctx.name(), null, this.requestOutboundHandler);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, HttpObject httpObject)
            throws Exception {
    	
    	inboundChannel.writeAndFlush(ReferenceCountUtil.retain(httpObject));

//        if (httpObject instanceof HttpResponse) {
//            currentRequest = null;
//            delayOutboundHandler.next();
//        }
    }

    public class RequestOutboundHandler extends ChannelOutboundHandlerAdapter {
    	private PendingWriteQueue requestsQueue;
    	private ChannelHandlerContext handlerContext;
    	
    	public synchronized void tryWritingRequests() {

    		if(!handlerContext.channel().isActive() || this.requestsQueue.isEmpty()) {
    			return;
    		}

    		if(this.requestsQueue.removeAndWriteAll() != null)
    			handlerContext.flush();
    	}
    	
    	@Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            handlerContext = ctx.pipeline().context(this);
    		requestsQueue = new PendingWriteQueue(ctx);
        }
    	
    	@Override
		public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
    		if(msg instanceof FullHttpRequest) {
    			HttpRequest request = (HttpRequest) msg;
    			requestsQueue.add(request, promise);
    		}
    		
    		tryWritingRequests();
    	}
    }
}
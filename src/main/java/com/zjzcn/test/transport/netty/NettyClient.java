package com.zjzcn.test.transport.netty;

import com.zjzcn.test.transport.Client;
import com.zjzcn.test.transport.Decoder;
import com.zjzcn.test.transport.Encoder;
import com.zjzcn.test.transport.Future;
import com.zjzcn.test.transport.FutureListener;
import com.zjzcn.test.transport.MessageHandler;
import com.zjzcn.test.transport.Protocols;
import com.zjzcn.test.transport.Request;
import com.zjzcn.test.transport.Response;
import com.zjzcn.test.transport.ResponseFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class NettyClient implements Client {
	
	private static Logger logger = LoggerFactory.getLogger(NettyClient.class);

	// 回收过期任务
	private static ScheduledExecutorService requestTimeoutExecutor = Executors.newScheduledThreadPool(4);
	// 异步的request，需要注册callback future
	// 触发remove的操作有： 1) service的返回结果处理。 2) timeout thread cancel
	private ConcurrentMap<String, ResponseFuture> callbackMap = new ConcurrentHashMap<>();
	private ScheduledFuture<?> requestTimeoutFuture = null;
	
	private EventLoopGroup eventLoopGroup;
	private Channel channel;
	private Bootstrap bootstrap;
	
	private String serverHost;
	private int serverPort;
	private int timeout = 10000;
	
	private SocketAddress localAddress;
	private SocketAddress remoteAddress;
	
	private AtomicLong errorCount = new AtomicLong(0);
	private AtomicLong sendCount = new AtomicLong(0);
	
	public NettyClient(String serverHost, int serverPort, Encoder encoder, Decoder decoder) {
		this.serverHost = serverHost;
		this.serverPort = serverPort;

		bootstrap = new Bootstrap();
		eventLoopGroup = new NioEventLoopGroup();
		bootstrap.group(eventLoopGroup)
		.channel(NioSocketChannel.class)
		.handler(new ChannelInitializer<SocketChannel>() {
			@Override
			public void initChannel(SocketChannel ch) throws Exception {
				ChannelPipeline pipeline = ch.pipeline();
				pipeline.addLast("decoder", new NettyEncoder(encoder));
				pipeline.addLast("encoder", new NettyDecoder(decoder));
				pipeline.addLast("handler", new NettyClientHandler(new MessageHandler() {
					@Override
					public Response handleRequest(Request request) {
						// Server should not push message to client, so noop.
						return null;
					}

					@Override
					public void handleResponse(Response response) {
						ResponseFuture responseFuture = removeCallback(response.getRequestId());
						if (responseFuture == null) {
							logger.warn("NettyClient has response from server, but responseFuture not exist,  requestId={}", response.getRequestId());
							return;
						}

						if (response.getException() != null) {
							responseFuture.onFailure(response);
						} else {
							responseFuture.onSuccess(response);
						}
					}
				}));
			}
		});

		requestTimeoutFuture = requestTimeoutExecutor.scheduleWithFixedDelay( new Runnable() {
			@Override
			public void run() {
				long currentTime = System.currentTimeMillis();
				for (Map.Entry<String, ResponseFuture> entry : callbackMap.entrySet()) {
					try {
						ResponseFuture future = entry.getValue();

						if (future.getCreateTime() + future.getTimeout() < currentTime) {
							// timeout: remove from callback list, and then cancel
							removeCallback(entry.getKey());
							future.cancel();
						} 
					} catch (Exception e) {
						logger.error("NettyClient clear timeout NettyResponseFuture Error:  remoteAddress=" + remoteAddress + " requestId=" + entry.getKey(), e);
					}
				}
			}
		}, Protocols.REQUEST_TIMEOUT_TIMER_PERIOD, Protocols.REQUEST_TIMEOUT_TIMER_PERIOD, TimeUnit.MILLISECONDS);
	}
	
	@Override
	public synchronized void connect() {
		logger.info("Http client connecting.");
		try {
			channel = bootstrap.connect(serverHost, serverPort).sync().channel();
			localAddress = channel.localAddress();
			remoteAddress = channel.remoteAddress();
			logger.info("Netty client connected. localAttress[{}], remoteAddress[{}].", localAddress, remoteAddress);
		} catch (Exception e) {
			logger.error("Netty client error while connecting.", e);
		}
	}

	@Override
	public synchronized void reconnect() {
		logger.info("Http client connecting.");
		try {
			channel.close();
			channel = bootstrap.connect(serverHost, serverPort).sync().channel();
			localAddress = channel.localAddress();
			remoteAddress = channel.remoteAddress();
			logger.info("Netty client connected. localAttress[{}], remoteAddress[{}].", localAddress, remoteAddress);
		} catch (Exception e) {
			logger.error("Netty client error while connecting.", e);
		}
	}
	
	@Override
	public synchronized void close() {
		logger.info("Http client closing.");
		try {
			// 取消定期的回收任务
			requestTimeoutFuture.cancel(true);
			// 关闭连接池
			eventLoopGroup.shutdownGracefully();
			channel.close();
			// 清空callback
			callbackMap.clear();

			logger.info("Netty client closed, remoteAddress={}", remoteAddress);
		} catch (Exception e) {
			logger.error("NettyClient close Error: remoteAddress={}", remoteAddress, e);
		}
	}

	@Override
	public Response send(Request request) {
		sendCount.incrementAndGet();
		ResponseFuture newResponseFuture = new ResponseFuture(request, timeout);
		registerCallback(request.getRequestId(), newResponseFuture);
		
		ChannelFuture writeFuture = this.channel.write(request);

		boolean result = writeFuture.awaitUninterruptibly(timeout, TimeUnit.MILLISECONDS);

		if(result && writeFuture.isSuccess()) {
			newResponseFuture.addListener(new FutureListener() {
				@Override
				public void onComplete(Future future) throws Exception {
					if (!future.isSuccess() || (future.isDone() && future.getException()!=null)) {
						// 失败的调用 
						errorCount.incrementAndGet();
					}
				}
			});
			return newResponseFuture;	
		} else {
			ResponseFuture responseFuture = removeCallback(request.getRequestId());

			if (responseFuture != null) {
				responseFuture.cancel();
			}

			// 失败的调用 
			errorCount.incrementAndGet();

			String errorMsg = "Error while sending request to server, remoteAddress=" + remoteAddress
					+ ", localAddress=" + localAddress +", requestId=" +  request.getRequestId();
			if (writeFuture.cause()!= null) {
				throw new RuntimeException(errorMsg, writeFuture.cause());
			} else {
				throw new RuntimeException(errorMsg);
			}
		}
	}

	@Override
	public boolean isConnected() {
		if(channel == null) {
			return false;
		}
		return channel.isActive();
	}
	
	@Override
	public SocketAddress getLocalAddress() {
		return localAddress;
	}

	@Override
	public SocketAddress getRemoteAddress() {
		return remoteAddress;
	}
	
	public void registerCallback(String requestId, ResponseFuture nettyResponseFuture) {
		if (this.callbackMap.size() >= Protocols.CLIENT_MAX_REQUEST) {
			throw new RuntimeException("Client over max concurrent request, drop request, remoteAddress="
					+ remoteAddress + " requestId=" + requestId);
		}
		this.callbackMap.put(requestId, nettyResponseFuture);
	}
	
	public ResponseFuture removeCallback(String requestId) {
		return callbackMap.remove(requestId);
	}

}

package netkernel;

import java.nio.charset.Charset;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class NetKernelServer {

	private static Logger logger = LoggerFactory.getLogger(NetKernelServer.class);


	public static void main(String[] args) throws Exception {
		EventLoopGroup bossGroup = new NioEventLoopGroup(1);
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		try {
			ServerBootstrap b = new ServerBootstrap();
			b.option(ChannelOption.SO_BACKLOG, 1024);
			b.group(bossGroup, workerGroup)
					.channel(NioServerSocketChannel.class)
					.handler(new LoggingHandler(LogLevel.DEBUG))
					.childHandler(new NetKernelChannelInitializer());

			Channel ch = b.bind(8080).sync().channel();
			ch.closeFuture().sync();
		}
		finally {
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
	}


	private static class NetKernelChannelInitializer extends ChannelInitializer<SocketChannel> {

		@Override
		public void initChannel(SocketChannel ch) {
			ChannelPipeline p = ch.pipeline();
			p.addLast(new HttpServerCodec());
			p.addLast(new HttpObjectAggregator(1024));
			p.addLast(new ChannelInboundHandlerAdapter() {

				@Override
				public void channelReadComplete(ChannelHandlerContext ctx) {
					ctx.flush();
				}

				@Override
				public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
					if (msg instanceof FullHttpRequest) {
						FullHttpResponse response = new DefaultFullHttpResponse(
								HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
								Unpooled.wrappedBuffer("Hello World".getBytes(Charset.forName("UTF-8"))));
						response.headers().set("Content-Type", "text/plain");
						ctx.write(response).addListener(ChannelFutureListener.CLOSE);
					}
				}

				@Override
				public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
					cause.printStackTrace();
					ctx.close();
				}
			});
		}

	}

}


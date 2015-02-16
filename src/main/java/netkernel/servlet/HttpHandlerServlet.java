package netkernel.servlet;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import demo.BufferingEchoHttpHandler;
import demo.CountingHttpHandler;
import demo.EchoHttpHandler;
import demo.SimpleEchoHttpHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import netkernel.HttpHandler;
import netkernel.HttpRequest;
import netkernel.HttpResponse;
import rx.Observable;
import rx.Subscriber;

/**
 * @author Arjen Poutsma
 */
@WebServlet(asyncSupported = true, urlPatterns = "/rx")
public class HttpHandlerServlet extends HttpServlet {

	private static final int BUFFER_SIZE = 4096;

	private HttpHandler httpHandler = new EchoHttpHandler();

	@Override
	protected void service(HttpServletRequest request, final HttpServletResponse response)
			throws ServletException, IOException {

		AsyncContext context = request.startAsync(request, response);
		final AsyncContextSynchronizer contextSynchronizer =
				new AsyncContextSynchronizer(context);

		ObservableReadListener readListener =
				new ObservableReadListener(contextSynchronizer);
		request.getInputStream().setReadListener(readListener);

		final ObservableWriteListener writeListener =
				new ObservableWriteListener(contextSynchronizer);
		response.getOutputStream().setWriteListener(writeListener);

		ServletHttpRequest handlerRequest =
				new ServletHttpRequest(Observable.create(readListener));
		final ServletHttpResponse handlerResponse = new ServletHttpResponse();

		this.httpHandler.handle(handlerRequest, handlerResponse)
				.subscribe(new Subscriber<Void>() {
					@Override
					public void onCompleted() {
						if (handlerResponse.body != null) {
							handlerResponse.body.subscribe(writeListener);
						}
						else {
							contextSynchronizer.writeComplete();
						}
					}

					@Override
					public void onError(Throwable e) {
						e.printStackTrace();
					}

					@Override
					public void onNext(Void aVoid) {
						// ignore
					}
				});
	}

	private static class ServletHttpRequest implements HttpRequest {

		private final Observable<ByteBuf> content;

		public ServletHttpRequest(Observable<ByteBuf> content) {
			this.content = content;
		}

		@Override
		public Observable<ByteBuf> getBody() {
			return content;
		}
	}

	private static class ObservableReadListener
			implements ReadListener, Observable.OnSubscribe<ByteBuf> {

		private final byte[] buffer = new byte[BUFFER_SIZE];

		private final AsyncContextSynchronizer synchronizer;

		private final ServletInputStream input;

		private Subscriber<? super ByteBuf> subscriber;

		public ObservableReadListener(AsyncContextSynchronizer synchronizer)
				throws IOException {
			this.synchronizer = synchronizer;
			this.input = this.synchronizer.getInputStream();
		}

		@Override
		public void call(Subscriber<? super ByteBuf> subscriber) {
			this.subscriber = subscriber;
		}

		@Override
		public void onDataAvailable() throws IOException {
			ByteBuf buf = Unpooled.buffer(BUFFER_SIZE);

			while (input.isReady()) {
				int read = this.input.read(this.buffer);
				buf.writeBytes(buffer, 0, read);
			}

			if (isSubscribed()) {
				this.subscriber.onNext(buf);
			}
		}

		private boolean isSubscribed() {
			return this.subscriber != null && !this.subscriber.isUnsubscribed();
		}

		@Override
		public void onAllDataRead() throws IOException {
			this.synchronizer.readComplete();
			if (isSubscribed()) {
				this.subscriber.onCompleted();
			}
		}

		@Override
		public void onError(Throwable throwable) {
			this.synchronizer.readComplete();
			if (isSubscribed()) {
				this.subscriber.onError(throwable);
			}
		}
	}

	private static class ServletHttpResponse implements HttpResponse {

		private Observable<ByteBuf> body;

		@Override
		public void setBody(Observable<ByteBuf> body) {
			this.body = body;
		}
	}

	private static class ObservableWriteListener extends Subscriber<ByteBuf>
			implements WriteListener {

		private final AsyncContextSynchronizer synchronizer;

		private final ServletOutputStream output;

		private final byte[] buffer = new byte[BUFFER_SIZE];

		private Queue<byte[]> queue = new LinkedBlockingQueue<byte[]>();

		private AtomicBoolean subscriberComplete = new AtomicBoolean(false);

		public ObservableWriteListener(AsyncContextSynchronizer synchronizer)
				throws IOException {
			this.synchronizer = synchronizer;
			this.output = synchronizer.getOutputStream();
		}

		// Subscriber implementation

		@Override
		public void onStart() {
			request(1);
		}

		@Override
		public void onNext(ByteBuf buf) {
			while (buf.readableBytes() > 0) {
				int len = Math.min(BUFFER_SIZE, buf.readableBytes());
				byte[] block = new byte[len];
				buf.readBytes(block);
				this.queue.add(block);
			}
		}

		@Override
		public void onCompleted() {
			subscriberComplete.compareAndSet(false, true);
		}

		// WriteListener implementation

		@Override
		public void onWritePossible() throws IOException {
			while (!this.queue.isEmpty() && this.output.isReady()) {
				output.write(queue.poll());
			}
			if (this.queue.isEmpty()) {
				if (!this.subscriberComplete.get()) {
					request(1);
				}
				else {
					this.synchronizer.writeComplete();
				}
			}
		}

		@Override
		public void onError(Throwable t) {
			t.printStackTrace();
			this.synchronizer.writeComplete();
		}
	}


}

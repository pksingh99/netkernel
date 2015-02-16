package demo;

import io.netty.buffer.ByteBuf;
import netkernel.HttpHandler;
import netkernel.HttpRequest;
import netkernel.HttpResponse;
import rx.Observable;
import rx.Subscriber;

/**
 * @author Arjen Poutsma
 */
public class SimpleEchoHttpHandler implements HttpHandler {

	@Override
	public Observable<Void> handle(final HttpRequest request,
			final HttpResponse response) {
		response.setBody(request.getBody());
		return Observable.create(new Observable.OnSubscribe<Void>() {
			@Override
			public void call(final Subscriber<? super Void> returnValueSubscriber) {

				request.getBody().subscribe(new Subscriber<ByteBuf>() {
					@Override
					public void onCompleted() {
						returnValueSubscriber.onNext(null);
						returnValueSubscriber.onCompleted();
					}

					@Override
					public void onError(Throwable e) {
						returnValueSubscriber.onError(e);
					}

					@Override
					public void onNext(ByteBuf byteBuf) {

					}
				});
			}
		});
	}
}


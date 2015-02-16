package demo;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import netkernel.HttpHandler;
import netkernel.HttpRequest;
import netkernel.HttpResponse;
import rx.Observable;
import rx.Subscriber;

/**
 * @author Arjen Poutsma
 */
public class EchoHttpHandler implements HttpHandler {

	@Override
	public Observable<Void> handle(final HttpRequest request,
			final HttpResponse response) {
		return Observable.create(new Observable.OnSubscribe<Void>() {
			@Override
			public void call(final Subscriber<? super Void> returnValueSubscriber) {

				response.setBody(Observable.create(new Observable.OnSubscribe<ByteBuf>() {
					@Override
					public void call(
							final Subscriber<? super ByteBuf> responseSubscriber) {
						request.getBody().subscribe(new Subscriber<ByteBuf>() {
							@Override
							public void onNext(ByteBuf byteBuf) {
								responseSubscriber.onNext(byteBuf);
							}

							@Override
							public void onCompleted() {
								responseSubscriber.onCompleted();
								returnValueSubscriber.onCompleted();
							}

							@Override
							public void onError(Throwable e) {
								responseSubscriber.onError(e);
								returnValueSubscriber.onError(e);
							}
						});

					}
				}));

			}
		});
	}
}


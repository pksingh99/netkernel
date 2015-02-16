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
public class BufferingEchoHttpHandler implements HttpHandler {

	@Override
	public Observable<Void> handle(final HttpRequest request,
			final HttpResponse response) {

		return Observable.create(new Observable.OnSubscribe<Void>() {
			@Override
			public void call(final Subscriber<? super Void> subscriber) {
				request.getBody().subscribe(new Subscriber<ByteBuf>() {
					List<ByteBuf> responseBody = new ArrayList<>();

					@Override
					public void onNext(ByteBuf buf) {
						responseBody.add(buf);
					}

					@Override
					public void onCompleted() {
						response.setBody(Observable.from(responseBody));
						subscriber.onCompleted();
					}

					@Override
					public void onError(Throwable e) {
						subscriber.onError(e);
					}
				});
			}
		});

	}
}



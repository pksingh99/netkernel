package demo;

import java.nio.charset.Charset;

import io.netty.buffer.ByteBuf;
import netkernel.HttpHandler;
import netkernel.HttpRequest;
import netkernel.HttpResponse;
import rx.Observable;
import rx.Subscriber;

/**
 * @author Arjen Poutsma
 */
public class CountingHttpHandler implements HttpHandler {

	@Override
	public Observable<Void> handle(final HttpRequest request,
			final HttpResponse response) {

		return Observable.create(new Observable.OnSubscribe<Void>() {
			@Override
			public void call(final Subscriber<? super Void> subscriber) {
				request.getBody().subscribe(new Subscriber<ByteBuf>() {

					private int bufCount = 0;
					@Override
					public void onNext(ByteBuf buf) {
						bufCount++;
					}

					@Override
					public void onCompleted() {
						System.out.println("Processed " + bufCount + " buffers");
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

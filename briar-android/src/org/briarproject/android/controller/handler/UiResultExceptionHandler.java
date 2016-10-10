package org.briarproject.android.controller.handler;

import android.support.annotation.UiThread;

import org.briarproject.android.DestroyableContext;

public abstract class UiResultExceptionHandler<R, E extends Exception>
		implements ResultExceptionHandler<R, E> {

	private final DestroyableContext listener;

	protected UiResultExceptionHandler(DestroyableContext listener) {
		this.listener = listener;
	}

	@Override
	public void onResult(final R result) {
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				onResultUi(result);
			}
		});
	}

	@Override
	public void onException(final E exception) {
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				onExceptionUi(exception);
			}
		});
	}

	@UiThread
	public abstract void onResultUi(R result);

	@UiThread
	public abstract void onExceptionUi(E exception);
}

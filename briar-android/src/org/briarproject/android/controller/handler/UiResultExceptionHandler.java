package org.briarproject.android.controller.handler;

import android.app.Activity;
import android.support.annotation.UiThread;

public abstract class UiResultExceptionHandler<R, E extends Exception>
		implements ResultExceptionHandler<R, E> {

	private final Activity activity;

	public UiResultExceptionHandler(Activity activity) {
		this.activity = activity;
	}

	@Override
	public void onResult(final R result) {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				onResultUi(result);
			}
		});
	}

	@Override
	public void onException(final E exception) {
		activity.runOnUiThread(new Runnable() {
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

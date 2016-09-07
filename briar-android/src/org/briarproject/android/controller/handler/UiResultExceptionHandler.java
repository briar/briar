package org.briarproject.android.controller.handler;

import android.support.annotation.UiThread;

import org.briarproject.android.fragment.BaseFragment.BaseFragmentListener;

public abstract class UiResultExceptionHandler<R, E extends Exception>
		implements ResultExceptionHandler<R, E> {

	private final BaseFragmentListener listener;

	protected UiResultExceptionHandler(BaseFragmentListener listener) {
		this.listener = listener;
	}

	@Override
	public void onResult(final R result) {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (!listener.hasBeenDestroyed())
					onResultUi(result);
			}
		});
	}

	@Override
	public void onException(final E exception) {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (!listener.hasBeenDestroyed())
					onExceptionUi(exception);
			}
		});
	}

	@UiThread
	public abstract void onResultUi(R result);

	@UiThread
	public abstract void onExceptionUi(E exception);
}

package org.briarproject.android.controller.handler;

import android.support.annotation.UiThread;

import org.briarproject.android.DestroyableActivity;
import org.briarproject.android.fragment.BaseFragment.BaseFragmentListener;

public abstract class UiResultHandler<R> implements ResultHandler<R> {

	private final DestroyableActivity listener;

	protected UiResultHandler(DestroyableActivity listener) {
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

	@UiThread
	public abstract void onResultUi(R result);
}

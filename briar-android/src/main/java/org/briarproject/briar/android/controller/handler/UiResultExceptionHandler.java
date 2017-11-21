package org.briarproject.briar.android.controller.handler;

import android.support.annotation.UiThread;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.DestroyableContext;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public abstract class UiResultExceptionHandler<R, E extends Exception>
		extends UiExceptionHandler<E> implements ResultExceptionHandler<R, E> {

	protected UiResultExceptionHandler(DestroyableContext listener) {
		super(listener);
	}

	@Override
	public void onResult(R result) {
		listener.runOnUiThreadUnlessDestroyed(() -> onResultUi(result));
	}

	@UiThread
	public abstract void onResultUi(R result);

}

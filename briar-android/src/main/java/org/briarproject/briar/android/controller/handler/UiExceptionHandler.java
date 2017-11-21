package org.briarproject.briar.android.controller.handler;

import android.support.annotation.UiThread;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.DestroyableContext;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public abstract class UiExceptionHandler<E extends Exception>
		implements ExceptionHandler<E> {

	protected final DestroyableContext listener;

	protected UiExceptionHandler(DestroyableContext listener) {
		this.listener = listener;
	}

	@Override
	public void onException(E exception) {
		listener.runOnUiThreadUnlessDestroyed(() -> onExceptionUi(exception));
	}

	@UiThread
	public abstract void onExceptionUi(E exception);

}

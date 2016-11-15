package org.briarproject.android.controller.handler;

import android.support.annotation.Nullable;
import android.support.annotation.UiThread;

import org.briarproject.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * This class defines a result handler with a callback that runs on the UI thread
 * and is retained when orientation changes occur. It ensures that the callback
 * is always run in the latest context.
 * <p>
 * Note that this event handler should be used carefully with callbacks that
 * depend on global variables as those variables might be destroyed or lose their
 * state during the orientation change.
 *
 * @param <R> The result's object type
 */
@Immutable
@NotNullByDefault
public abstract class UiContextExceptionResultHandler<R, E extends Exception>
		extends UiContextResultHandler<R>
		implements ResultExceptionHandler<R, E> {

	@Nullable
	private E exception;

	protected UiContextExceptionResultHandler(
			DestroyableContextManager listener, String tag) {
		super(listener, tag);
	}

	@Override
	public void setDestroyableContextManager(
			DestroyableContextManager listener) {
		boolean isSwitchingFromNullToNonNull =
				this.listener == null && listener != null;
		super.setDestroyableContextManager(listener);
		if (isSwitchingFromNullToNonNull)
			runException();
	}

	@Override
	public void onException(final E exception) {
		this.exception = exception;
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				runException();
			}
		});
	}

	@UiThread
	private void runException() {
		if (exception != null && listener != null) {
			onExceptionUi(exception, listener);
			listener.removeContextResultHandler(getTag());
			exception = null;
		}
	}

	@UiThread
	public abstract void onExceptionUi(E exception,
			DestroyableContextManager context);
}

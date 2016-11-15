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
public abstract class UiContextResultHandler<R>
		implements ContextResultHandler<R> {

	private final String tag;
	protected DestroyableContextManager listener;
	@Nullable
	private R result;

	@UiThread
	protected UiContextResultHandler(DestroyableContextManager listener,
			String tag) {
		this.listener = listener;
		this.tag = tag;
		listener.addContextResultHandler(this);
	}

	@UiThread
	@Override
	public void setDestroyableContextManager(
			DestroyableContextManager listener) {
		// Check if the listener is switching from null to non null
		boolean isSwitchingFromNullToNonNull =
				this.listener == null && listener != null;
		this.listener = listener;
		if (isSwitchingFromNullToNonNull)
			runResult();
	}

	@Override
	public String getTag() {
		return tag;
	}

	@Override
	public DestroyableContextManager getContextManager() {
		return listener;
	}

	@Override
	public void onResult(final R result) {
		this.result = result;
		listener.runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				runResult();
			}
		});
	}

	@UiThread
	private void runResult() {
		if (result != null && listener != null) {
			onResultUi(result, listener);
			listener.removeContextResultHandler(tag);
			result = null;
		}
	}

	@UiThread
	public abstract void onResultUi(R result,
			DestroyableContextManager context);
}

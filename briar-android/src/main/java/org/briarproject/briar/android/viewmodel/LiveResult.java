package org.briarproject.briar.android.viewmodel;

import android.support.annotation.Nullable;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public class LiveResult<T> {

	@Nullable
	private T result;
	@Nullable
	private Exception exception;

	public LiveResult(T result) {
		this.result = result;
		this.exception = null;
	}

	public LiveResult(Exception exception) {
		this.result = null;
		this.exception = exception;
	}

	@Nullable
	public T getResultOrNull() {
		return result;
	}

	@Nullable
	public Exception getException() {
		return exception;
	}

	public boolean hasError() {
		return exception != null;
	}

}

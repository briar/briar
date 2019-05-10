package org.briarproject.briar.android.viewmodel;

import android.support.annotation.Nullable;
import android.support.annotation.StringRes;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public class LiveResult<T> {

	@Nullable
	private T result;
	@StringRes
	private int errorRes;

	public LiveResult(T result) {
		this.result = result;
		this.errorRes = 0;
	}

	public LiveResult(@StringRes int errorRes) {
		this.result = null;
		this.errorRes = errorRes;
	}

	@Nullable
	public T getResultOrNull() {
		return result;
	}

	@StringRes
	public int getErrorRes() {
		return errorRes;
	}

	public boolean hasError() {
		return errorRes != 0;
	}

}

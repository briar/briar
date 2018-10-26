package org.briarproject.briar.android.util;

import android.support.annotation.Nullable;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;


@NotNullByDefault
public interface Runnable<T> {

	void run(@Nullable T t);

}

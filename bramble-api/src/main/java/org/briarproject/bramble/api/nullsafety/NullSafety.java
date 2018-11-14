package org.briarproject.bramble.api.nullsafety;

import javax.annotation.Nullable;

@NotNullByDefault
public class NullSafety {

	/**
	 * Stand-in for `Objects.requireNonNull()`.
	 */
	public static <T> T requireNonNull(@Nullable T t) {
		if (t == null) throw new NullPointerException();
		return t;
	}
}

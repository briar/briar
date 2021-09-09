package org.briarproject.bramble.api.nullsafety;

import javax.annotation.Nullable;

@NotNullByDefault
public class NullSafety {

	/**
	 * Stand-in for {@code Objects.requireNonNull()}.
	 */
	public static <T> T requireNonNull(@Nullable T t) {
		if (t == null) throw new NullPointerException();
		return t;
	}

	/**
	 * Checks that exactly one of the arguments is null.
	 *
	 * @throws AssertionError If both or neither of the arguments are null
	 */
	public static void requireExactlyOneNull(@Nullable Object a,
			@Nullable Object b) {
		if ((a == null) == (b == null)) throw new AssertionError();
	}

	/**
	 * Checks that the argument is null.
	 */
	public static void requireNull(@Nullable Object o) {
		if (o != null) throw new AssertionError();
	}

	/**
	 * Stand-in for {@code Objects.equals()}.
	 */
	public static boolean equals(@Nullable Object a, @Nullable Object b) {
		return (a == b) || (a != null && a.equals(b));
	}

}

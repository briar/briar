package org.briarproject.bramble.api;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.NoSuchElementException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Minimal stand-in for `java.util.Optional`. Functionality from `Optional`
 * can be added as needed.
 */
@Immutable
@NotNullByDefault
public class Maybe<T> {

	@Nullable
	private final T value;

	public Maybe(@Nullable T value) {
		this.value = value;
	}

	public boolean isPresent() {
		return value != null;
	}

	public T get() {
		if (value == null) throw new NoSuchElementException();
		return value;
	}
}

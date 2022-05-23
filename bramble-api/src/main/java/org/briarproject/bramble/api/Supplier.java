package org.briarproject.bramble.api;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface Supplier<T> {

	T get();
}

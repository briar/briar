package org.briarproject.bramble.api;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface Consumer<T> {

	void accept(T t);
}

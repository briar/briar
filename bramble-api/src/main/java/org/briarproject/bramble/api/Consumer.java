package org.briarproject.bramble.api;

import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface Consumer<T> {

	void accept(T t);
}

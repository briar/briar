package org.briarproject.bramble.api;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface Predicate<T> {

	boolean test(T t);
}

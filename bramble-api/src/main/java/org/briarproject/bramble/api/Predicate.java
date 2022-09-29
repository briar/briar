package org.briarproject.bramble.api;

import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface Predicate<T> {

	boolean test(T t);
}

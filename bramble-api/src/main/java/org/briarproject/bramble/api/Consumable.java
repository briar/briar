package org.briarproject.bramble.api;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

/**
 * An item that can be consumed once.
 */
@NotNullByDefault
public class Consumable<T> {

	private final AtomicReference<T> reference;

	public Consumable(T item) {
		reference = new AtomicReference<>(item);
	}

	@Nullable
	public T consume() {
		return reference.getAndSet(null);
	}
}

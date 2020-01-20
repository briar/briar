package org.briarproject.bramble.api.event;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface EventBus {

	/**
	 * Adds a listener to be notified when events occur.
	 */
	void addListener(EventListener l);

	/**
	 * Removes a listener.
	 */
	void removeListener(EventListener l);

	/**
	 * Asynchronously notifies all listeners of an event. Listeners are
	 * notified on the {@link EventExecutor}.
	 * <p>
	 * This method can safely be called while holding a lock.
	 */
	void broadcast(Event e);
}

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
	 * Notifies all listeners of an event.
	 */
	void broadcast(Event e);
}

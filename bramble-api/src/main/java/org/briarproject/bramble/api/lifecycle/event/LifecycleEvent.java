package org.briarproject.bramble.api.lifecycle.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState;

/**
 * An event that is broadcast when the app enters a new lifecycle state.
 */
public class LifecycleEvent extends Event {

	private final LifecycleState state;

	public LifecycleEvent(LifecycleState state) {
		this.state = state;
	}

	public LifecycleState getLifecycleState() {
		return state;
	}
}

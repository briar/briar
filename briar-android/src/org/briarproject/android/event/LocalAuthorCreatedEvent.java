package org.briarproject.android.event;

import org.briarproject.api.event.Event;

public class LocalAuthorCreatedEvent extends Event {

	private final long handle;

	public LocalAuthorCreatedEvent(long handle) {
		this.handle = handle;
	}

	public long getAuthorHandle() {
		return handle;
	}
}

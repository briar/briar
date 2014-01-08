package org.briarproject.api.messaging;

import java.util.Collection;

/** A packet requesting one or more {@link Message}s from the recipient. */
public class Request {

	private final Collection<MessageId> requested;

	public Request(Collection<MessageId> requested) {
		this.requested = requested;
	}

	/** Returns the identifiers of the requested messages. */
	public Collection<MessageId> getMessageIds() {
		return requested;
	}
}

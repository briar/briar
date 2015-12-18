package org.briarproject.api.sync;

import java.util.Collection;

/**
 * A packet requesting one or more {@link Message Messages} from the recipient.
 */
public class Request {

	private final Collection<org.briarproject.api.sync.MessageId> requested;

	public Request(Collection<org.briarproject.api.sync.MessageId> requested) {
		this.requested = requested;
	}

	/** Returns the identifiers of the requested messages. */
	public Collection<org.briarproject.api.sync.MessageId> getMessageIds() {
		return requested;
	}
}

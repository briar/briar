package org.briarproject.bramble.api.sync;

import java.util.Collection;

/**
 * A record requesting one or more {@link Message Messages} from the recipient.
 */
public class Request {

	private final Collection<MessageId> requested;

	public Request(Collection<MessageId> requested) {
		this.requested = requested;
	}

	/**
	 * Returns the identifiers of the requested messages.
	 */
	public Collection<MessageId> getMessageIds() {
		return requested;
	}
}

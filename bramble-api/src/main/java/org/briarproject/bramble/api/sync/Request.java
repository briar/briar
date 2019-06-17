package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

/**
 * A record requesting one or more {@link Message Messages} from the recipient.
 */
@Immutable
@NotNullByDefault
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

package org.briarproject.bramble.api.sync;

import java.util.Collection;

/**
 * A record offering the recipient one or more {@link Message Messages}.
 */
public class Offer {

	private final Collection<MessageId> offered;

	public Offer(Collection<MessageId> offered) {
		this.offered = offered;
	}

	/**
	 * Returns the identifiers of the offered messages.
	 */
	public Collection<MessageId> getMessageIds() {
		return offered;
	}
}

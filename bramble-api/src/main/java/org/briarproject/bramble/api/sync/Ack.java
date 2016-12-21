package org.briarproject.bramble.api.sync;

import java.util.Collection;

/**
 * A record acknowledging receipt of one or more {@link Message Messages}.
 */
public class Ack {

	private final Collection<MessageId> acked;

	public Ack(Collection<MessageId> acked) {
		this.acked = acked;
	}

	/**
	 * Returns the identifiers of the acknowledged messages.
	 */
	public Collection<MessageId> getMessageIds() {
		return acked;
	}
}

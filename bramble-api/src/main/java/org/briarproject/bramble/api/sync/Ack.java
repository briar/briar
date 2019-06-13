package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

/**
 * A record acknowledging receipt of one or more {@link Message Messages}.
 */
@Immutable
@NotNullByDefault
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

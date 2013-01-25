package net.sf.briar.api.protocol;

import java.util.Collection;

/** A packet acknowledging receipt of one or more {@link Message}s. */
public class Ack {

	private final Collection<MessageId> acked;

	public Ack(Collection<MessageId> acked) {
		this.acked = acked;
	}

	/** Returns the identifiers of the acknowledged messages. */
	public Collection<MessageId> getMessageIds() {
		return acked;
	}
}

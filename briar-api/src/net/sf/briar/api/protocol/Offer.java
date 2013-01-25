package net.sf.briar.api.protocol;

import java.util.Collection;

/** A packet offering the recipient one or more {@link Messages}. */
public class Offer {

	private final Collection<MessageId> offered;

	public Offer(Collection<MessageId> offered) {
		this.offered = offered;
	}

	/** Returns the identifiers of the offered messages. */
	public Collection<MessageId> getMessageIds() {
		return offered;
	}
}

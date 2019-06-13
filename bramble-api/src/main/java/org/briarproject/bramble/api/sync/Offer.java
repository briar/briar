package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

/**
 * A record offering the recipient one or more {@link Message Messages}.
 */
@Immutable
@NotNullByDefault
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

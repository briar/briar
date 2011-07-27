package net.sf.briar.api.protocol;

import java.util.Collection;

/** A packet offering the recipient some messages. */
public interface Offer {

	/**
	 * The maximum size of a serialised offer, excluding encryption and
	 * authentication.
	 */
	static final int MAX_SIZE = (1024 * 1024) - 100;

	/** Returns the message IDs contained in the offer. */
	Collection<MessageId> getMessageIds();
}

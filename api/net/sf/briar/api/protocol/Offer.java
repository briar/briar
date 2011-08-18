package net.sf.briar.api.protocol;

import java.util.Collection;

/** A packet offering the recipient some messages. */
public interface Offer {

	/** Returns the offer's unique identifier. */
	OfferId getId();

	/** Returns the message IDs contained in the offer. */
	Collection<MessageId> getMessageIds();
}

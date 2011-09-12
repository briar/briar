package net.sf.briar.api.protocol;

import java.util.Collection;

/** A packet offering the recipient some messages. */
public interface Offer {

	/** The maximum number of message IDs per offer. */
	static final int MAX_IDS_PER_OFFER = 29959;

	/** Returns the message IDs contained in the offer. */
	Collection<MessageId> getMessageIds();
}

package net.sf.briar.api.protocol;

import java.util.Collection;

/** A packet containing messages. */
public interface Batch {

	/** Returns the batch's unique identifier. */
	BatchId getId();

	/** Returns the messages contained in the batch. */
	Collection<Message> getMessages();
}
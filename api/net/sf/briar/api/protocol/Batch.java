package net.sf.briar.api.protocol;

import java.util.Collection;

/** A packet containing messages. */
public interface Batch {

	/**
	 * The maximum size of a serialised batch, excluding encryption and
	 * authentication.
	 */
	static final int MAX_SIZE = (1024 * 1024) - 100;

	/** Returns the batch's unique identifier. */
	BatchId getId();

	/** Returns the messages contained in the batch. */
	Collection<Message> getMessages();
}
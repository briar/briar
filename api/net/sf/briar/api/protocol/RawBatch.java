package net.sf.briar.api.protocol;

import java.util.Collection;

/** An outgoing packet containing messages. */
public interface RawBatch {

	/** Returns the batch's unique identifier. */
	BatchId getId();

	/** Returns the serialised messages contained in the batch. */
	Collection<byte[]> getMessages();
}

package net.sf.briar.api.protocol.writers;

import java.io.IOException;

import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.OfferId;

/** An interface for creating a have notification. */
public interface OfferWriter {

	/**
	 * Attempts to add the given message ID to the offer and returns true if it
	 * was added.
	 */
	boolean writeMessageId(MessageId m) throws IOException;

	/** Finishes writing the offer and returns its unique identifier. */
	OfferId finish() throws IOException;
}

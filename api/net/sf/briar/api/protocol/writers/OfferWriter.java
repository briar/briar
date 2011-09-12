package net.sf.briar.api.protocol.writers;

import java.io.IOException;

import net.sf.briar.api.protocol.MessageId;

/** An interface for creating an offer packet. */
public interface OfferWriter {

	/**
	 * Attempts to add the given message ID to the offer and returns true if it
	 * was added.
	 */
	boolean writeMessageId(MessageId m) throws IOException;

	/** Finishes writing the offer. */
	void finish() throws IOException;
}

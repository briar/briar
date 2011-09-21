package net.sf.briar.api.protocol.writers;

import java.io.IOException;

import net.sf.briar.api.protocol.MessageId;

/** An interface for creating an offer packet. */
public interface OfferWriter {

	/**
	 * Sets the maximum length of the serialised offer. If this method is not
	 * called, the default is ProtocolConstants.MAX_PACKET_LENGTH;
	 */
	void setMaxPacketLength(int length);

	/**
	 * Attempts to add the given message ID to the offer and returns true if it
	 * was added.
	 */
	boolean writeMessageId(MessageId m) throws IOException;

	/** Finishes writing the offer. */
	void finish() throws IOException;
}

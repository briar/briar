package net.sf.briar.api.protocol.writers;

import java.io.IOException;

import net.sf.briar.api.protocol.BatchId;

/** An interface for creating a batch packet. */
public interface BatchWriter {

	/**
	 * Sets the maximum length of the serialised batch. If this method is not
	 * called, the default is ProtocolConstants.MAX_PACKET_LENGTH;
	 */
	void setMaxPacketLength(int length);

	/**
	 * Attempts to add the given raw message to the batch and returns true if
	 * it was added.
	 */
	boolean writeMessage(byte[] raw) throws IOException;

	/** Finishes writing the batch and returns its unique identifier. */
	BatchId finish() throws IOException;
}

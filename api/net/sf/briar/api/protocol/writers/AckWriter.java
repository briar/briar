package net.sf.briar.api.protocol.writers;

import java.io.IOException;

import net.sf.briar.api.protocol.BatchId;

/** An interface for creating an ack packet. */
public interface AckWriter {

	/**
	 * Sets the maximum length of the serialised ack. If this method is not
	 * called, the default is ProtocolConstants.MAX_PACKET_LENGTH;
	 */
	void setMaxPacketLength(int length);

	/**
	 * Attempts to add the given BatchId to the ack and returns true if it
	 * was added.
	 */
	boolean writeBatchId(BatchId b) throws IOException;

	/** Finishes writing the ack. */
	void finish() throws IOException;
}

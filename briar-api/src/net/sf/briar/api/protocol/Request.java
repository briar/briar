package net.sf.briar.api.protocol;

import java.util.BitSet;

/** A packet requesting some or all of the messages from an offer. */
public interface Request {

	/**
	 * Returns a sequence of bits corresponding to the sequence of messages in
	 * the offer, where the i^th bit is set if the i^th message should be sent.
	 */
	BitSet getBitmap();

	/** Returns the length of the bitmap in bits. */
	int getLength();
}

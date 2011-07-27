package net.sf.briar.api.protocol;

import java.util.BitSet;

/** A packet requesting some or all of the messages from an offer. */
public interface Request {

	/**
	 * The maximum size of a serialised request, exlcuding encryption and
	 * authentication.
	 */
	static final int MAX_SIZE = (1024 * 1024) - 100;

	/**
	 * Returns a sequence of bits corresponding to the sequence of messages in
	 * the offer, where the i^th bit is set if the i^th message should be sent.
	 */
	BitSet getBitmap();
}

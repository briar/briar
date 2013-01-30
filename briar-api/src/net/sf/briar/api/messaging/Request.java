package net.sf.briar.api.messaging;

import java.util.BitSet;

/**
 * A packet requesting some or all of the {@link Message}s from an
 * {@link Offer}.
 */
public class Request {

	private final BitSet requested;
	private final int length;

	public Request(BitSet requested, int length) {
		this.requested = requested;
		this.length = length;
	}

	/**
	 * Returns a sequence of bits corresponding to the sequence of messages in
	 * the offer, where the i^th bit is set if the i^th message should be sent.
	 */
	public BitSet getBitmap() {
		return requested;
	}

	/** Returns the length of the bitmap in bits. */
	public int getLength() {
		return length;
	}
}

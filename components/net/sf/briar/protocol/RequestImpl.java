package net.sf.briar.protocol;

import java.util.BitSet;

import net.sf.briar.api.protocol.Request;

class RequestImpl implements Request {

	private final BitSet requested;
	private final int length;

	RequestImpl(BitSet requested, int length) {
		this.requested = requested;
		this.length = length;
	}

	public BitSet getBitmap() {
		return requested;
	}

	public int getLength() {
		return length;
	}
}

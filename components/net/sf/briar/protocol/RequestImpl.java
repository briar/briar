package net.sf.briar.protocol;

import java.util.BitSet;

import net.sf.briar.api.protocol.Request;

class RequestImpl implements Request {

	private final BitSet requested;

	RequestImpl(BitSet requested) {
		this.requested = requested;
	}

	public BitSet getBitmap() {
		return requested;
	}
}

package net.sf.briar.protocol;

import java.util.BitSet;

import net.sf.briar.api.protocol.OfferId;
import net.sf.briar.api.protocol.Request;

class RequestImpl implements Request {

	private final OfferId offerId;
	private final BitSet requested;

	RequestImpl(OfferId offerId, BitSet requested) {
		this.offerId = offerId;
		this.requested = requested;
	}

	public OfferId getOfferId() {
		return offerId;
	}

	public BitSet getBitmap() {
		return requested;
	}
}

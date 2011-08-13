package net.sf.briar.protocol;

import java.util.BitSet;

import net.sf.briar.api.protocol.OfferId;
import net.sf.briar.api.protocol.Request;

class RequestFactoryImpl implements RequestFactory {

	public Request createRequest(OfferId offerId, BitSet requested) {
		return new RequestImpl(offerId, requested);
	}
}

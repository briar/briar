package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;

class OfferImpl implements Offer {

	private final Collection<MessageId> offered;

	OfferImpl(Collection<MessageId> offered) {
		this.offered = offered;
	}

	public Collection<MessageId> getMessageIds() {
		return offered;
	}
}

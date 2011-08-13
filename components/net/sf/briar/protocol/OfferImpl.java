package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.OfferId;

class OfferImpl implements Offer {

	private final OfferId id;
	private final Collection<MessageId> offered;

	OfferImpl(OfferId id, Collection<MessageId> offered) {
		this.id = id;
		this.offered = offered;
	}

	public OfferId getId() {
		return id;
	}

	public Collection<MessageId> getMessageIds() {
		return offered;
	}
}

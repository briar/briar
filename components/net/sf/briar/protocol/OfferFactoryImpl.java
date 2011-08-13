package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.OfferId;

class OfferFactoryImpl implements OfferFactory {

	public Offer createOffer(OfferId id, Collection<MessageId> offered) {
		return new OfferImpl(id, offered);
	}
}

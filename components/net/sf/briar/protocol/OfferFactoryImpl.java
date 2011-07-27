package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;

class OfferFactoryImpl implements OfferFactory {

	public Offer createOffer(Collection<MessageId> offered) {
		return new OfferImpl(offered);
	}
}

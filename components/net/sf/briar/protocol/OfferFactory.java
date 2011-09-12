package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;

interface OfferFactory {

	Offer createOffer(Collection<MessageId> offered);
}

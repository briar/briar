package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;

class OfferImpl implements Offer {

	private final Collection<MessageId> messages;

	OfferImpl(Collection<MessageId> messages) {
		this.messages = messages;
	}

	public Collection<MessageId> getMessages() {
		return messages;
	}
}

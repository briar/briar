package net.sf.briar.protocol;

import java.io.IOException;
import java.util.Collection;

import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

import com.google.inject.Inject;

class OfferReader implements ObjectReader<Offer> {

	private final ObjectReader<MessageId> messageIdReader;
	private final OfferFactory offerFactory;

	@Inject
	OfferReader(ObjectReader<MessageId> messageIdReader,
			OfferFactory offerFactory) {
		this.messageIdReader = messageIdReader;
		this.offerFactory = offerFactory;
	}

	public Offer readObject(Reader r) throws IOException {
		// Initialise the consumer
		Consumer counting = new CountingConsumer(Offer.MAX_SIZE);
		// Read the data
		r.addConsumer(counting);
		r.readUserDefinedTag(Tags.OFFER);
		r.addObjectReader(Tags.MESSAGE_ID, messageIdReader);
		Collection<MessageId> messages = r.readList(MessageId.class);
		r.removeObjectReader(Tags.MESSAGE_ID);
		r.removeConsumer(counting);
		// Build and return the offer
		return offerFactory.createOffer(messages);
	}
}

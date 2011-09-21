package net.sf.briar.protocol;

import java.io.IOException;
import java.util.Collection;

import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class OfferReader implements ObjectReader<Offer> {

	private final ObjectReader<MessageId> messageIdReader;
	private final OfferFactory offerFactory;

	OfferReader(ObjectReader<MessageId> messageIdReader,
			OfferFactory offerFactory) {
		this.messageIdReader = messageIdReader;
		this.offerFactory = offerFactory;
	}

	public Offer readObject(Reader r) throws IOException {
		// Initialise the consumer
		Consumer counting =
			new CountingConsumer(ProtocolConstants.MAX_PACKET_LENGTH);
		// Read the data
		r.addConsumer(counting);
		r.readUserDefinedId(Types.OFFER);
		r.addObjectReader(Types.MESSAGE_ID, messageIdReader);
		Collection<MessageId> messages = r.readList(MessageId.class);
		r.removeObjectReader(Types.MESSAGE_ID);
		r.removeConsumer(counting);
		// Build and return the offer
		return offerFactory.createOffer(messages);
	}
}

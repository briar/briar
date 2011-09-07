package net.sf.briar.protocol;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Collection;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.OfferId;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class OfferReader implements ObjectReader<Offer> {

	private final MessageDigest messageDigest;
	private final ObjectReader<MessageId> messageIdReader;
	private final OfferFactory offerFactory;

	OfferReader(CryptoComponent crypto, ObjectReader<MessageId> messageIdReader,
			OfferFactory offerFactory) {
		messageDigest = crypto.getMessageDigest();
		this.messageIdReader = messageIdReader;
		this.offerFactory = offerFactory;
	}

	public Offer readObject(Reader r) throws IOException {
		// Initialise the consumers
		Consumer counting =
			new CountingConsumer(ProtocolConstants.MAX_PACKET_LENGTH);
		DigestingConsumer digesting = new DigestingConsumer(messageDigest);
		messageDigest.reset();
		// Read the data
		r.addConsumer(counting);
		r.addConsumer(digesting);
		r.readUserDefinedTag(Tags.OFFER);
		r.addObjectReader(Tags.MESSAGE_ID, messageIdReader);
		Collection<MessageId> messages = r.readList(MessageId.class);
		if(messages.size() > Offer.MAX_IDS_PER_OFFER)
			throw new FormatException();
		r.removeObjectReader(Tags.MESSAGE_ID);
		r.removeConsumer(digesting);
		r.removeConsumer(counting);
		// Build and return the offer
		OfferId id = new OfferId(messageDigest.digest());
		return offerFactory.createOffer(id, messages);
	}
}

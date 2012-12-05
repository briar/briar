package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.briar.api.Bytes;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.PacketFactory;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.StructReader;
import net.sf.briar.api.serial.Reader;

class OfferReader implements StructReader<Offer> {

	private final PacketFactory packetFactory;

	OfferReader(PacketFactory packetFactory) {
		this.packetFactory = packetFactory;
	}

	public Offer readStruct(Reader r) throws IOException {
		// Initialise the consumer
		Consumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		// Read the data
		r.addConsumer(counting);
		r.readStructId(Types.OFFER);
		r.setMaxBytesLength(UniqueId.LENGTH);
		List<Bytes> raw = r.readList(Bytes.class);
		r.resetMaxBytesLength();
		r.removeConsumer(counting);
		if(raw.isEmpty()) throw new FormatException();
		// Convert the byte arrays to message IDs
		List<MessageId> messages = new ArrayList<MessageId>();
		for(Bytes b : raw) {
			if(b.getBytes().length != UniqueId.LENGTH)
				throw new FormatException();
			messages.add(new MessageId(b.getBytes()));
		}
		// Build and return the offer
		return packetFactory.createOffer(Collections.unmodifiableList(
				messages));
	}
}

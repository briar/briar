package net.sf.briar.messaging;

import static net.sf.briar.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_PROPERTY_LENGTH;
import static net.sf.briar.api.messaging.Types.ACK;
import static net.sf.briar.api.messaging.Types.MESSAGE;
import static net.sf.briar.api.messaging.Types.OFFER;
import static net.sf.briar.api.messaging.Types.REQUEST;
import static net.sf.briar.api.messaging.Types.RETENTION_ACK;
import static net.sf.briar.api.messaging.Types.RETENTION_UPDATE;
import static net.sf.briar.api.messaging.Types.SUBSCRIPTION_ACK;
import static net.sf.briar.api.messaging.Types.SUBSCRIPTION_UPDATE;
import static net.sf.briar.api.messaging.Types.TRANSPORT_ACK;
import static net.sf.briar.api.messaging.Types.TRANSPORT_UPDATE;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.sf.briar.api.Bytes;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.messaging.Ack;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.messaging.Offer;
import net.sf.briar.api.messaging.PacketReader;
import net.sf.briar.api.messaging.Request;
import net.sf.briar.api.messaging.RetentionAck;
import net.sf.briar.api.messaging.RetentionUpdate;
import net.sf.briar.api.messaging.SubscriptionAck;
import net.sf.briar.api.messaging.SubscriptionUpdate;
import net.sf.briar.api.messaging.TransportAck;
import net.sf.briar.api.messaging.TransportId;
import net.sf.briar.api.messaging.TransportUpdate;
import net.sf.briar.api.messaging.UniqueId;
import net.sf.briar.api.messaging.UnverifiedMessage;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.StructReader;

// This class is not thread-safe
class PacketReaderImpl implements PacketReader {

	private final StructReader<UnverifiedMessage> messageReader;
	private final StructReader<SubscriptionUpdate> subscriptionUpdateReader;
	private final Reader r;

	PacketReaderImpl(ReaderFactory readerFactory,
			StructReader<UnverifiedMessage> messageReader,
			StructReader<SubscriptionUpdate> subscriptionUpdateReader,
			InputStream in) {
		this.messageReader = messageReader;
		this.subscriptionUpdateReader = subscriptionUpdateReader;
		r = readerFactory.createReader(in);
	}

	public boolean eof() throws IOException {
		return r.eof();
	}

	public boolean hasAck() throws IOException {
		return r.hasStruct(ACK);
	}

	public Ack readAck() throws IOException {
		Consumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		r.addConsumer(counting);
		r.readStructId(ACK);
		// Read the message IDs as byte arrays
		r.setMaxBytesLength(UniqueId.LENGTH);
		List<Bytes> raw = r.readList(Bytes.class);
		r.resetMaxBytesLength();
		r.removeConsumer(counting);
		if(raw.isEmpty()) throw new FormatException();
		// Convert the byte arrays to message IDs
		List<MessageId> acked = new ArrayList<MessageId>();
		for(Bytes b : raw) {
			if(b.getBytes().length != UniqueId.LENGTH)
				throw new FormatException();
			acked.add(new MessageId(b.getBytes()));
		}
		// Build and return the ack
		return new Ack(Collections.unmodifiableList(acked));
	}

	public boolean hasMessage() throws IOException {
		return r.hasStruct(MESSAGE);
	}

	public UnverifiedMessage readMessage() throws IOException {
		return messageReader.readStruct(r);
	}

	public boolean hasOffer() throws IOException {
		return r.hasStruct(OFFER);
	}

	public Offer readOffer() throws IOException {
		Consumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		r.addConsumer(counting);
		r.readStructId(OFFER);
		// Read the message IDs as byte arrays
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
		return new Offer(Collections.unmodifiableList(messages));
	}

	public boolean hasRequest() throws IOException {
		return r.hasStruct(REQUEST);
	}

	public Request readRequest() throws IOException {
		Consumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		r.addConsumer(counting);
		r.readStructId(REQUEST);
		// There may be up to 7 bits of padding at the end of the bitmap
		int padding = r.readUint7();
		if(padding > 7) throw new FormatException();
		// Read the bitmap
		byte[] bitmap = r.readBytes(MAX_PACKET_LENGTH);
		r.removeConsumer(counting);
		// Convert the bitmap into a BitSet
		int length = bitmap.length * 8 - padding;
		BitSet b = new BitSet(length);
		for(int i = 0; i < bitmap.length; i++) {
			for(int j = 0; j < 8 && i * 8 + j < length; j++) {
				byte bit = (byte) (128 >> j);
				if((bitmap[i] & bit) != 0) b.set(i * 8 + j);
			}
		}
		return new Request(b, length);
	}

	public boolean hasRetentionAck() throws IOException {
		return r.hasStruct(RETENTION_ACK);
	}

	public RetentionAck readRetentionAck() throws IOException {
		r.readStructId(RETENTION_ACK);
		long version = r.readInt64();
		if(version < 0L) throw new FormatException();
		return new RetentionAck(version);
	}

	public boolean hasRetentionUpdate() throws IOException {
		return r.hasStruct(RETENTION_UPDATE);
	}

	public RetentionUpdate readRetentionUpdate() throws IOException {
		r.readStructId(RETENTION_UPDATE);
		long retention = r.readInt64();
		if(retention < 0L) throw new FormatException();
		long version = r.readInt64();
		if(version < 0L) throw new FormatException();
		return new RetentionUpdate(retention, version);
	}

	public boolean hasSubscriptionAck() throws IOException {
		return r.hasStruct(SUBSCRIPTION_ACK);
	}

	public SubscriptionAck readSubscriptionAck() throws IOException {
		r.readStructId(SUBSCRIPTION_ACK);
		long version = r.readInt64();
		if(version < 0L) throw new FormatException();
		return new SubscriptionAck(version);
	}

	public boolean hasSubscriptionUpdate() throws IOException {
		return r.hasStruct(SUBSCRIPTION_UPDATE);
	}

	public SubscriptionUpdate readSubscriptionUpdate() throws IOException {
		return subscriptionUpdateReader.readStruct(r);
	}

	public boolean hasTransportAck() throws IOException {
		return r.hasStruct(TRANSPORT_ACK);
	}

	public TransportAck readTransportAck() throws IOException {
		r.readStructId(TRANSPORT_ACK);
		byte[] b = r.readBytes(UniqueId.LENGTH);
		if(b.length < UniqueId.LENGTH) throw new FormatException();
		long version = r.readInt64();
		if(version < 0L) throw new FormatException();
		return new TransportAck(new TransportId(b), version);
	}

	public boolean hasTransportUpdate() throws IOException {
		return r.hasStruct(TRANSPORT_UPDATE);
	}

	public TransportUpdate readTransportUpdate() throws IOException {
		Consumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		r.addConsumer(counting);
		r.readStructId(TRANSPORT_UPDATE);
		// Read the transport ID
		byte[] b = r.readBytes(UniqueId.LENGTH);
		if(b.length < UniqueId.LENGTH) throw new FormatException();
		TransportId id = new TransportId(b);
		// Read the transport properties
		r.setMaxStringLength(MAX_PROPERTY_LENGTH);
		Map<String, String> m = r.readMap(String.class, String.class);
		r.resetMaxStringLength();
		if(m.size() > MAX_PROPERTIES_PER_TRANSPORT)
			throw new FormatException();
		// Read the version number
		long version = r.readInt64();
		if(version < 0L) throw new FormatException();
		r.removeConsumer(counting);
		// Build and return the transport update
		return new TransportUpdate(id, new TransportProperties(m), version);
	}
}

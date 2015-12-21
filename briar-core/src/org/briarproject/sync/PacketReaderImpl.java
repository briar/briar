package org.briarproject.sync;

import org.briarproject.api.FormatException;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.UniqueId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.sync.Ack;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.Offer;
import org.briarproject.api.sync.PacketReader;
import org.briarproject.api.sync.Request;
import org.briarproject.api.sync.SubscriptionAck;
import org.briarproject.api.sync.SubscriptionUpdate;
import org.briarproject.api.sync.TransportAck;
import org.briarproject.api.sync.TransportUpdate;
import org.briarproject.util.ByteUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.briarproject.api.TransportPropertyConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static org.briarproject.api.TransportPropertyConstants.MAX_PROPERTY_LENGTH;
import static org.briarproject.api.TransportPropertyConstants.MAX_TRANSPORT_ID_LENGTH;
import static org.briarproject.api.sync.PacketTypes.ACK;
import static org.briarproject.api.sync.PacketTypes.MESSAGE;
import static org.briarproject.api.sync.PacketTypes.OFFER;
import static org.briarproject.api.sync.PacketTypes.REQUEST;
import static org.briarproject.api.sync.PacketTypes.SUBSCRIPTION_ACK;
import static org.briarproject.api.sync.PacketTypes.SUBSCRIPTION_UPDATE;
import static org.briarproject.api.sync.PacketTypes.TRANSPORT_ACK;
import static org.briarproject.api.sync.PacketTypes.TRANSPORT_UPDATE;
import static org.briarproject.api.sync.SyncConstants.MAX_PACKET_PAYLOAD_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.api.sync.SyncConstants.PACKET_HEADER_LENGTH;
import static org.briarproject.api.sync.SyncConstants.PROTOCOL_VERSION;

// This class is not thread-safe
class PacketReaderImpl implements PacketReader {

	private enum State { BUFFER_EMPTY, BUFFER_FULL, EOF }

	private final CryptoComponent crypto;
	private final BdfReaderFactory bdfReaderFactory;
	private final ObjectReader<SubscriptionUpdate> subscriptionUpdateReader;
	private final InputStream in;
	private final byte[] header, payload;

	private State state = State.BUFFER_EMPTY;
	private int payloadLength = 0;

	PacketReaderImpl(CryptoComponent crypto, BdfReaderFactory bdfReaderFactory,
			ObjectReader<SubscriptionUpdate> subscriptionUpdateReader,
			InputStream in) {
		this.crypto = crypto;
		this.bdfReaderFactory = bdfReaderFactory;
		this.subscriptionUpdateReader = subscriptionUpdateReader;
		this.in = in;
		header = new byte[PACKET_HEADER_LENGTH];
		payload = new byte[MAX_PACKET_PAYLOAD_LENGTH];
	}

	private void readPacket() throws IOException {
		if (state != State.BUFFER_EMPTY) throw new IllegalStateException();
		// Read the header
		int offset = 0;
		while (offset < PACKET_HEADER_LENGTH) {
			int read = in.read(header, offset, PACKET_HEADER_LENGTH - offset);
			if (read == -1) {
				if (offset > 0) throw new FormatException();
				state = State.EOF;
				return;
			}
			offset += read;
		}
		// Check the protocol version
		if (header[0] != PROTOCOL_VERSION) throw new FormatException();
		// Read the payload length
		payloadLength = ByteUtils.readUint16(header, 2);
		if (payloadLength > MAX_PACKET_PAYLOAD_LENGTH) throw new FormatException();
		// Read the payload
		offset = 0;
		while (offset < payloadLength) {
			int read = in.read(payload, offset, payloadLength - offset);
			if (read == -1) throw new FormatException();
			offset += read;
		}
		state = State.BUFFER_FULL;
	}

	public boolean eof() throws IOException {
		if (state == State.BUFFER_EMPTY) readPacket();
		if (state == State.BUFFER_EMPTY) throw new IllegalStateException();
		return state == State.EOF;
	}

	public boolean hasAck() throws IOException {
		return !eof() && header[1] == ACK;
	}

	public Ack readAck() throws IOException {
		if (!hasAck()) throw new FormatException();
		return new Ack(Collections.unmodifiableList(readMessageIds()));
	}

	private List<MessageId> readMessageIds() throws IOException {
		if (payloadLength == 0) throw new FormatException();
		if (payloadLength % UniqueId.LENGTH != 0) throw new FormatException();
		List<MessageId> ids = new ArrayList<MessageId>();
		for (int off = 0; off < payloadLength; off += UniqueId.LENGTH) {
			byte[] id = new byte[UniqueId.LENGTH];
			System.arraycopy(payload, off, id, 0, UniqueId.LENGTH);
			ids.add(new MessageId(id));
		}
		state = State.BUFFER_EMPTY;
		return ids;
	}

	public boolean hasMessage() throws IOException {
		return !eof() && header[1] == MESSAGE;
	}

	public Message readMessage() throws IOException {
		if (!hasMessage()) throw new FormatException();
		if (payloadLength <= MESSAGE_HEADER_LENGTH) throw new FormatException();
		// Group ID
		byte[] id = new byte[UniqueId.LENGTH];
		System.arraycopy(payload, 0, id, 0, UniqueId.LENGTH);
		GroupId groupId = new GroupId(id);
		// Timestamp
		long timestamp = ByteUtils.readUint64(payload, UniqueId.LENGTH);
		if (timestamp < 0) throw new FormatException();
		// Raw message
		byte[] raw = new byte[payloadLength];
		System.arraycopy(payload, 0, raw, 0, payloadLength);
		state = State.BUFFER_EMPTY;
		// Message ID
		MessageId messageId = new MessageId(crypto.hash(MessageId.LABEL, raw));
		return new Message(messageId, groupId, timestamp, raw);
	}

	public boolean hasOffer() throws IOException {
		return !eof() && header[1] == OFFER;
	}

	public Offer readOffer() throws IOException {
		if (!hasOffer()) throw new FormatException();
		return new Offer(Collections.unmodifiableList(readMessageIds()));
	}

	public boolean hasRequest() throws IOException {
		return !eof() && header[1] == REQUEST;
	}

	public Request readRequest() throws IOException {
		if (!hasRequest()) throw new FormatException();
		return new Request(Collections.unmodifiableList(readMessageIds()));
	}

	public boolean hasSubscriptionAck() throws IOException {
		return !eof() && header[1] == SUBSCRIPTION_ACK;
	}

	public SubscriptionAck readSubscriptionAck() throws IOException {
		if (!hasSubscriptionAck()) throw new FormatException();
		// Set up the reader
		InputStream bais = new ByteArrayInputStream(payload, 0, payloadLength);
		BdfReader r = bdfReaderFactory.createReader(bais);
		// Read the start of the payload
		r.readListStart();
		// Read the version
		long version = r.readInteger();
		if (version < 0) throw new FormatException();
		// Read the end of the payload
		r.readListEnd();
		if (!r.eof()) throw new FormatException();
		state = State.BUFFER_EMPTY;
		// Build and return the subscription ack
		return new SubscriptionAck(version);
	}

	public boolean hasSubscriptionUpdate() throws IOException {
		return !eof() && header[1] == SUBSCRIPTION_UPDATE;
	}

	public SubscriptionUpdate readSubscriptionUpdate() throws IOException {
		if (!hasSubscriptionUpdate()) throw new FormatException();
		// Set up the reader
		InputStream bais = new ByteArrayInputStream(payload, 0, payloadLength);
		BdfReader r = bdfReaderFactory.createReader(bais);
		// Read and build the subscription update
		SubscriptionUpdate u = subscriptionUpdateReader.readObject(r);
		if (!r.eof()) throw new FormatException();
		state = State.BUFFER_EMPTY;
		return u;
	}

	public boolean hasTransportAck() throws IOException {
		return !eof() && header[1] == TRANSPORT_ACK;
	}

	public TransportAck readTransportAck() throws IOException {
		if (!hasTransportAck()) throw new FormatException();
		// Set up the reader
		InputStream bais = new ByteArrayInputStream(payload, 0, payloadLength);
		BdfReader r = bdfReaderFactory.createReader(bais);
		// Read the start of the payload
		r.readListStart();
		// Read the transport ID and version
		String idString = r.readString(MAX_TRANSPORT_ID_LENGTH);
		if (idString.length() == 0) throw new FormatException();
		TransportId id = new TransportId(idString);
		long version = r.readInteger();
		if (version < 0) throw new FormatException();
		// Read the end of the payload
		r.readListEnd();
		if (!r.eof()) throw new FormatException();
		state = State.BUFFER_EMPTY;
		// Build and return the transport ack
		return new TransportAck(id, version);
	}

	public boolean hasTransportUpdate() throws IOException {
		return !eof() && header[1] == TRANSPORT_UPDATE;
	}

	public TransportUpdate readTransportUpdate() throws IOException {
		if (!hasTransportUpdate()) throw new FormatException();
		// Set up the reader
		InputStream bais = new ByteArrayInputStream(payload, 0, payloadLength);
		BdfReader r = bdfReaderFactory.createReader(bais);
		// Read the start of the payload
		r.readListStart();
		// Read the transport ID
		String idString = r.readString(MAX_TRANSPORT_ID_LENGTH);
		if (idString.length() == 0) throw new FormatException();
		TransportId id = new TransportId(idString);
		// Read the transport properties
		Map<String, String> p = new HashMap<String, String>();
		r.readDictionaryStart();
		for (int i = 0; !r.hasDictionaryEnd(); i++) {
			if (i == MAX_PROPERTIES_PER_TRANSPORT)
				throw new FormatException();
			String key = r.readString(MAX_PROPERTY_LENGTH);
			String value = r.readString(MAX_PROPERTY_LENGTH);
			p.put(key, value);
		}
		r.readDictionaryEnd();
		// Read the version number
		long version = r.readInteger();
		if (version < 0) throw new FormatException();
		// Read the end of the payload
		r.readListEnd();
		if (!r.eof()) throw new FormatException();
		state = State.BUFFER_EMPTY;
		// Build and return the transport update
		return new TransportUpdate(id, new TransportProperties(p), version);
	}
}

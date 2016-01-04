package org.briarproject.sync;

import org.briarproject.api.FormatException;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.UniqueId;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.sync.Ack;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.Offer;
import org.briarproject.api.sync.PacketReader;
import org.briarproject.api.sync.Request;
import org.briarproject.api.sync.SubscriptionAck;
import org.briarproject.api.sync.SubscriptionUpdate;
import org.briarproject.api.sync.TransportAck;
import org.briarproject.api.sync.TransportUpdate;
import org.briarproject.api.sync.UnverifiedMessage;
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
import static org.briarproject.api.sync.MessagingConstants.HEADER_LENGTH;
import static org.briarproject.api.sync.MessagingConstants.MAX_PAYLOAD_LENGTH;
import static org.briarproject.api.sync.MessagingConstants.PROTOCOL_VERSION;
import static org.briarproject.api.sync.PacketTypes.ACK;
import static org.briarproject.api.sync.PacketTypes.MESSAGE;
import static org.briarproject.api.sync.PacketTypes.OFFER;
import static org.briarproject.api.sync.PacketTypes.REQUEST;
import static org.briarproject.api.sync.PacketTypes.SUBSCRIPTION_ACK;
import static org.briarproject.api.sync.PacketTypes.SUBSCRIPTION_UPDATE;
import static org.briarproject.api.sync.PacketTypes.TRANSPORT_ACK;
import static org.briarproject.api.sync.PacketTypes.TRANSPORT_UPDATE;

// This class is not thread-safe
class PacketReaderImpl implements PacketReader {

	private enum State { BUFFER_EMPTY, BUFFER_FULL, EOF }

	private final BdfReaderFactory bdfReaderFactory;
	private final ObjectReader<UnverifiedMessage> messageReader;
	private final ObjectReader<SubscriptionUpdate> subscriptionUpdateReader;
	private final InputStream in;
	private final byte[] header, payload;

	private State state = State.BUFFER_EMPTY;
	private int payloadLength = 0;

	PacketReaderImpl(BdfReaderFactory bdfReaderFactory,
			ObjectReader<UnverifiedMessage> messageReader,
			ObjectReader<SubscriptionUpdate> subscriptionUpdateReader,
			InputStream in) {
		this.bdfReaderFactory = bdfReaderFactory;
		this.messageReader = messageReader;
		this.subscriptionUpdateReader = subscriptionUpdateReader;
		this.in = in;
		header = new byte[HEADER_LENGTH];
		payload = new byte[MAX_PAYLOAD_LENGTH];
	}

	private void readPacket() throws IOException {
		assert state == State.BUFFER_EMPTY;
		// Read the header
		int offset = 0;
		while (offset < HEADER_LENGTH) {
			int read = in.read(header, offset, HEADER_LENGTH - offset);
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
		if (payloadLength > MAX_PAYLOAD_LENGTH) throw new FormatException();
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
		assert state != State.BUFFER_EMPTY;
		return state == State.EOF;
	}

	public boolean hasAck() throws IOException {
		return !eof() && header[1] == ACK;
	}

	public Ack readAck() throws IOException {
		if (!hasAck()) throw new FormatException();
		// Set up the reader
		InputStream bais = new ByteArrayInputStream(payload, 0, payloadLength);
		BdfReader r = bdfReaderFactory.createReader(bais);
		// Read the start of the payload
		r.readListStart();
		// Read the message IDs
		List<MessageId> acked = new ArrayList<MessageId>();
		r.readListStart();
		while (!r.hasListEnd()) {
			byte[] b = r.readRaw(UniqueId.LENGTH);
			if (b.length != UniqueId.LENGTH)
				throw new FormatException();
			acked.add(new MessageId(b));
		}
		if (acked.isEmpty()) throw new FormatException();
		r.readListEnd();
		// Read the end of the payload
		r.readListEnd();
		if (!r.eof()) throw new FormatException();
		state = State.BUFFER_EMPTY;
		// Build and return the ack
		return new Ack(Collections.unmodifiableList(acked));
	}

	public boolean hasMessage() throws IOException {
		return !eof() && header[1] == MESSAGE;
	}

	public UnverifiedMessage readMessage() throws IOException {
		if (!hasMessage()) throw new FormatException();
		// Set up the reader
		InputStream bais = new ByteArrayInputStream(payload, 0, payloadLength);
		BdfReader r = bdfReaderFactory.createReader(bais);
		// Read and build the message
		UnverifiedMessage m = messageReader.readObject(r);
		if (!r.eof()) throw new FormatException();
		state = State.BUFFER_EMPTY;
		return m;
	}

	public boolean hasOffer() throws IOException {
		return !eof() && header[1] == OFFER;
	}

	public Offer readOffer() throws IOException {
		if (!hasOffer()) throw new FormatException();
		// Set up the reader
		InputStream bais = new ByteArrayInputStream(payload, 0, payloadLength);
		BdfReader r = bdfReaderFactory.createReader(bais);
		// Read the start of the payload
		r.readListStart();
		// Read the message IDs
		List<MessageId> offered = new ArrayList<MessageId>();
		r.readListStart();
		while (!r.hasListEnd()) {
			byte[] b = r.readRaw(UniqueId.LENGTH);
			if (b.length != UniqueId.LENGTH)
				throw new FormatException();
			offered.add(new MessageId(b));
		}
		if (offered.isEmpty()) throw new FormatException();
		r.readListEnd();
		// Read the end of the payload
		r.readListEnd();
		if (!r.eof()) throw new FormatException();
		state = State.BUFFER_EMPTY;
		// Build and return the offer
		return new Offer(Collections.unmodifiableList(offered));
	}

	public boolean hasRequest() throws IOException {
		return !eof() && header[1] == REQUEST;
	}

	public Request readRequest() throws IOException {
		if (!hasRequest()) throw new FormatException();
		// Set up the reader
		InputStream bais = new ByteArrayInputStream(payload, 0, payloadLength);
		BdfReader r = bdfReaderFactory.createReader(bais);
		// Read the start of the payload
		r.readListStart();
		// Read the message IDs
		r.readListStart();
		List<MessageId> requested = new ArrayList<MessageId>();
		while (!r.hasListEnd()) {
			byte[] b = r.readRaw(UniqueId.LENGTH);
			if (b.length != UniqueId.LENGTH)
				throw new FormatException();
			requested.add(new MessageId(b));
		}
		if (requested.isEmpty()) throw new FormatException();
		r.readListEnd();
		// Read the end of the payload
		r.readListEnd();
		if (!r.eof()) throw new FormatException();
		state = State.BUFFER_EMPTY;
		// Build and return the request
		return new Request(Collections.unmodifiableList(requested));
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

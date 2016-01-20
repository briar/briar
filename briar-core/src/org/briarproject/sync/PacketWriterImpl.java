package org.briarproject.sync;

import org.briarproject.api.UniqueId;
import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.sync.Ack;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.Offer;
import org.briarproject.api.sync.PacketTypes;
import org.briarproject.api.sync.PacketWriter;
import org.briarproject.api.sync.Request;
import org.briarproject.api.sync.SubscriptionAck;
import org.briarproject.api.sync.SubscriptionUpdate;
import org.briarproject.api.sync.TransportAck;
import org.briarproject.api.sync.TransportUpdate;
import org.briarproject.util.ByteUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static org.briarproject.api.sync.PacketTypes.ACK;
import static org.briarproject.api.sync.PacketTypes.OFFER;
import static org.briarproject.api.sync.PacketTypes.REQUEST;
import static org.briarproject.api.sync.PacketTypes.SUBSCRIPTION_ACK;
import static org.briarproject.api.sync.PacketTypes.SUBSCRIPTION_UPDATE;
import static org.briarproject.api.sync.PacketTypes.TRANSPORT_ACK;
import static org.briarproject.api.sync.PacketTypes.TRANSPORT_UPDATE;
import static org.briarproject.api.sync.SyncConstants.MAX_PACKET_PAYLOAD_LENGTH;
import static org.briarproject.api.sync.SyncConstants.PACKET_HEADER_LENGTH;
import static org.briarproject.api.sync.SyncConstants.PROTOCOL_VERSION;

// This class is not thread-safe
class PacketWriterImpl implements PacketWriter {

	private final BdfWriterFactory bdfWriterFactory;
	private final OutputStream out;
	private final byte[] header;
	private final ByteArrayOutputStream payload;

	PacketWriterImpl(BdfWriterFactory bdfWriterFactory, OutputStream out) {
		this.bdfWriterFactory = bdfWriterFactory;
		this.out = out;
		header = new byte[PACKET_HEADER_LENGTH];
		header[0] = PROTOCOL_VERSION;
		payload = new ByteArrayOutputStream(MAX_PACKET_PAYLOAD_LENGTH);
	}

	public int getMaxMessagesForAck(long capacity) {
		return getMaxMessagesForPacket(capacity);
	}

	public int getMaxMessagesForRequest(long capacity) {
		return getMaxMessagesForPacket(capacity);
	}

	public int getMaxMessagesForOffer(long capacity) {
		return getMaxMessagesForPacket(capacity);
	}

	private int getMaxMessagesForPacket(long capacity) {
		int payload = (int) Math.min(capacity - PACKET_HEADER_LENGTH,
				MAX_PACKET_PAYLOAD_LENGTH);
		return payload / UniqueId.LENGTH;
	}

	private void writePacket(byte packetType) throws IOException {
		header[1] = packetType;
		ByteUtils.writeUint16(payload.size(), header, 2);
		out.write(header);
		payload.writeTo(out);
		payload.reset();
	}

	public void writeAck(Ack a) throws IOException {
		if (payload.size() != 0) throw new IllegalStateException();
		for (MessageId m : a.getMessageIds()) payload.write(m.getBytes());
		writePacket(ACK);
	}

	public void writeMessage(byte[] raw) throws IOException {
		header[1] = PacketTypes.MESSAGE;
		ByteUtils.writeUint16(raw.length, header, 2);
		out.write(header);
		out.write(raw);
	}

	public void writeOffer(Offer o) throws IOException {
		if (payload.size() != 0) throw new IllegalStateException();
		for (MessageId m : o.getMessageIds()) payload.write(m.getBytes());
		writePacket(OFFER);
	}

	public void writeRequest(Request r) throws IOException {
		if (payload.size() != 0) throw new IllegalStateException();
		for (MessageId m : r.getMessageIds()) payload.write(m.getBytes());
		writePacket(REQUEST);
	}

	public void writeSubscriptionAck(SubscriptionAck a) throws IOException {
		if (payload.size() != 0) throw new IllegalStateException();
		BdfWriter w = bdfWriterFactory.createWriter(payload);
		w.writeListStart();
		w.writeInteger(a.getVersion());
		w.writeListEnd();
		writePacket(SUBSCRIPTION_ACK);
	}

	public void writeSubscriptionUpdate(SubscriptionUpdate u)
			throws IOException {
		if (payload.size() != 0) throw new IllegalStateException();
		BdfWriter w = bdfWriterFactory.createWriter(payload);
		w.writeListStart();
		w.writeListStart();
		for (Group g : u.getGroups()) {
			w.writeListStart();
			w.writeRaw(g.getClientId().getBytes());
			w.writeRaw(g.getDescriptor());
			w.writeListEnd();
		}
		w.writeListEnd();
		w.writeInteger(u.getVersion());
		w.writeListEnd();
		writePacket(SUBSCRIPTION_UPDATE);
	}

	public void writeTransportAck(TransportAck a) throws IOException {
		if (payload.size() != 0) throw new IllegalStateException();
		BdfWriter w = bdfWriterFactory.createWriter(payload);
		w.writeListStart();
		w.writeString(a.getId().getString());
		w.writeInteger(a.getVersion());
		w.writeListEnd();
		writePacket(TRANSPORT_ACK);
	}

	public void writeTransportUpdate(TransportUpdate u) throws IOException {
		if (payload.size() != 0) throw new IllegalStateException();
		BdfWriter w = bdfWriterFactory.createWriter(payload);
		w.writeListStart();
		w.writeString(u.getId().getString());
		w.writeDictionary(u.getProperties());
		w.writeInteger(u.getVersion());
		w.writeListEnd();
		writePacket(TRANSPORT_UPDATE);
	}

	public void flush() throws IOException {
		out.flush();
	}
}

package org.briarproject.messaging;

import static org.briarproject.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;
import static org.briarproject.api.messaging.Types.ACK;
import static org.briarproject.api.messaging.Types.GROUP;
import static org.briarproject.api.messaging.Types.OFFER;
import static org.briarproject.api.messaging.Types.REQUEST;
import static org.briarproject.api.messaging.Types.RETENTION_ACK;
import static org.briarproject.api.messaging.Types.RETENTION_UPDATE;
import static org.briarproject.api.messaging.Types.SUBSCRIPTION_ACK;
import static org.briarproject.api.messaging.Types.SUBSCRIPTION_UPDATE;
import static org.briarproject.api.messaging.Types.TRANSPORT_ACK;
import static org.briarproject.api.messaging.Types.TRANSPORT_UPDATE;

import java.io.IOException;
import java.io.OutputStream;

import org.briarproject.api.messaging.Ack;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.MessageId;
import org.briarproject.api.messaging.Offer;
import org.briarproject.api.messaging.PacketWriter;
import org.briarproject.api.messaging.Request;
import org.briarproject.api.messaging.RetentionAck;
import org.briarproject.api.messaging.RetentionUpdate;
import org.briarproject.api.messaging.SubscriptionAck;
import org.briarproject.api.messaging.SubscriptionUpdate;
import org.briarproject.api.messaging.TransportAck;
import org.briarproject.api.messaging.TransportUpdate;
import org.briarproject.api.serial.SerialComponent;
import org.briarproject.api.serial.Writer;
import org.briarproject.api.serial.WriterFactory;

// This class is not thread-safe
class PacketWriterImpl implements PacketWriter {

	private final SerialComponent serial;
	private final OutputStream out;
	private final boolean flush;
	private final Writer w;

	PacketWriterImpl(SerialComponent serial, WriterFactory writerFactory,
			OutputStream out, boolean flush) {
		this.serial = serial;
		this.out = out;
		this.flush = flush;
		w = writerFactory.createWriter(out);
	}

	public int getMaxMessagesForRequest(long capacity) {
		int packet = (int) Math.min(capacity, MAX_PACKET_LENGTH);
		int overhead = serial.getSerialisedStructStartLength(ACK)
				+ serial.getSerialisedListStartLength()
				+ serial.getSerialisedListEndLength()
				+ serial.getSerialisedStructEndLength();
		int idLength = serial.getSerialisedUniqueIdLength();
		return (packet - overhead) / idLength;
	}

	public int getMaxMessagesForOffer(long capacity) {
		int packet = (int) Math.min(capacity, MAX_PACKET_LENGTH);
		int overhead = serial.getSerialisedStructStartLength(OFFER)
				+ serial.getSerialisedListStartLength()
				+ serial.getSerialisedListEndLength()
				+ serial.getSerialisedStructEndLength();
		int idLength = serial.getSerialisedUniqueIdLength();
		return (packet - overhead) / idLength;
	}

	public void writeAck(Ack a) throws IOException {
		w.writeStructStart(ACK);
		w.writeListStart();
		for(MessageId m : a.getMessageIds()) w.writeBytes(m.getBytes());
		w.writeListEnd();
		w.writeStructEnd();
		if(flush) out.flush();
	}

	public void writeMessage(byte[] raw) throws IOException {
		out.write(raw);
		if(flush) out.flush();
	}

	public void writeOffer(Offer o) throws IOException {
		w.writeStructStart(OFFER);
		w.writeListStart();
		for(MessageId m : o.getMessageIds()) w.writeBytes(m.getBytes());
		w.writeListEnd();
		w.writeStructEnd();
		if(flush) out.flush();
	}

	public void writeRequest(Request r) throws IOException {
		w.writeStructStart(REQUEST);
		w.writeListStart();
		for(MessageId m : r.getMessageIds()) w.writeBytes(m.getBytes());
		w.writeListEnd();
		w.writeStructEnd();
		if(flush) out.flush();
	}

	public void writeRetentionAck(RetentionAck a) throws IOException {
		w.writeStructStart(RETENTION_ACK);
		w.writeIntAny(a.getVersion());
		w.writeStructEnd();
		if(flush) out.flush();
	}

	public void writeRetentionUpdate(RetentionUpdate u) throws IOException {
		w.writeStructStart(RETENTION_UPDATE);
		w.writeIntAny(u.getRetentionTime());
		w.writeIntAny(u.getVersion());
		w.writeStructEnd();
		if(flush) out.flush();
	}

	public void writeSubscriptionAck(SubscriptionAck a) throws IOException {
		w.writeStructStart(SUBSCRIPTION_ACK);
		w.writeIntAny(a.getVersion());
		w.writeStructEnd();
		if(flush) out.flush();
	}

	public void writeSubscriptionUpdate(SubscriptionUpdate u)
			throws IOException {
		w.writeStructStart(SUBSCRIPTION_UPDATE);
		w.writeListStart();
		for(Group g : u.getGroups()) {
			w.writeStructStart(GROUP);
			w.writeString(g.getName());
			w.writeBytes(g.getSalt());
			w.writeStructEnd();
		}
		w.writeListEnd();
		w.writeIntAny(u.getVersion());
		w.writeStructEnd();
		if(flush) out.flush();
	}

	public void writeTransportAck(TransportAck a) throws IOException {
		w.writeStructStart(TRANSPORT_ACK);
		w.writeBytes(a.getId().getBytes());
		w.writeIntAny(a.getVersion());
		w.writeStructEnd();
		if(flush) out.flush();
	}

	public void writeTransportUpdate(TransportUpdate u) throws IOException {
		w.writeStructStart(TRANSPORT_UPDATE);
		w.writeBytes(u.getId().getBytes());
		w.writeMap(u.getProperties());
		w.writeIntAny(u.getVersion());
		w.writeStructEnd();
		if(flush) out.flush();
	}

	public void flush() throws IOException {
		out.flush();
	}

	public void close() throws IOException {
		out.close();
	}
}

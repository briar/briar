package net.sf.briar.messaging;

import static net.sf.briar.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.messaging.Types.ACK;
import static net.sf.briar.api.messaging.Types.GROUP;
import static net.sf.briar.api.messaging.Types.OFFER;
import static net.sf.briar.api.messaging.Types.REQUEST;
import static net.sf.briar.api.messaging.Types.RETENTION_ACK;
import static net.sf.briar.api.messaging.Types.RETENTION_UPDATE;
import static net.sf.briar.api.messaging.Types.SUBSCRIPTION_ACK;
import static net.sf.briar.api.messaging.Types.SUBSCRIPTION_UPDATE;
import static net.sf.briar.api.messaging.Types.TRANSPORT_ACK;
import static net.sf.briar.api.messaging.Types.TRANSPORT_UPDATE;

import java.io.IOException;
import java.io.OutputStream;

import net.sf.briar.api.messaging.Ack;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.messaging.Offer;
import net.sf.briar.api.messaging.PacketWriter;
import net.sf.briar.api.messaging.Request;
import net.sf.briar.api.messaging.RetentionAck;
import net.sf.briar.api.messaging.RetentionUpdate;
import net.sf.briar.api.messaging.SubscriptionAck;
import net.sf.briar.api.messaging.SubscriptionUpdate;
import net.sf.briar.api.messaging.TransportAck;
import net.sf.briar.api.messaging.TransportUpdate;
import net.sf.briar.api.serial.SerialComponent;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

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

	public int getMaxMessagesForAck(long capacity) {
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

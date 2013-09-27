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
import java.util.BitSet;

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
		int overhead = serial.getSerialisedStructIdLength(ACK)
				+ serial.getSerialisedListStartLength()
				+ serial.getSerialisedListEndLength();
		int idLength = serial.getSerialisedUniqueIdLength();
		return (packet - overhead) / idLength;
	}

	public int getMaxMessagesForOffer(long capacity) {
		int packet = (int) Math.min(capacity, MAX_PACKET_LENGTH);
		int overhead = serial.getSerialisedStructIdLength(OFFER)
				+ serial.getSerialisedListStartLength()
				+ serial.getSerialisedListEndLength();
		int idLength = serial.getSerialisedUniqueIdLength();
		return (packet - overhead) / idLength;
	}

	public void writeAck(Ack a) throws IOException {
		w.writeStructId(ACK);
		w.writeListStart();
		for(MessageId m : a.getMessageIds()) w.writeBytes(m.getBytes());
		w.writeListEnd();
		if(flush) out.flush();
	}

	public void writeMessage(byte[] raw) throws IOException {
		out.write(raw);
		if(flush) out.flush();
	}

	public void writeOffer(Offer o) throws IOException {
		w.writeStructId(OFFER);
		w.writeListStart();
		for(MessageId m : o.getMessageIds()) w.writeBytes(m.getBytes());
		w.writeListEnd();
		if(flush) out.flush();
	}

	public void writeRequest(Request r) throws IOException {
		BitSet b = r.getBitmap();
		int length = r.getLength();
		// If the number of bits isn't a multiple of 8, round up to a byte
		int bytes = length % 8 == 0 ? length / 8 : length / 8 + 1;
		byte[] bitmap = new byte[bytes];
		// I'm kind of surprised BitSet doesn't have a method for this
		for(int i = 0; i < length; i++) {
			if(b.get(i)) {
				int offset = i / 8;
				byte bit = (byte) (128 >> i % 8);
				bitmap[offset] |= bit;
			}
		}
		w.writeStructId(REQUEST);
		w.writeUint7((byte) (bytes * 8 - length));
		w.writeBytes(bitmap);
		if(flush) out.flush();
	}

	public void writeRetentionAck(RetentionAck a) throws IOException {
		w.writeStructId(RETENTION_ACK);
		w.writeInt64(a.getVersion());
		if(flush) out.flush();
	}

	public void writeRetentionUpdate(RetentionUpdate u) throws IOException {
		w.writeStructId(RETENTION_UPDATE);
		w.writeInt64(u.getRetentionTime());
		w.writeInt64(u.getVersion());
		if(flush) out.flush();
	}

	public void writeSubscriptionAck(SubscriptionAck a) throws IOException {
		w.writeStructId(SUBSCRIPTION_ACK);
		w.writeInt64(a.getVersion());
		if(flush) out.flush();
	}

	public void writeSubscriptionUpdate(SubscriptionUpdate u)
			throws IOException {
		w.writeStructId(SUBSCRIPTION_UPDATE);
		w.writeListStart();
		for(Group g : u.getGroups()) {
			w.writeStructId(GROUP);
			w.writeString(g.getName());
			w.writeBytes(g.getSalt());
		}
		w.writeListEnd();
		w.writeInt64(u.getVersion());
		if(flush) out.flush();
	}

	public void writeTransportAck(TransportAck a) throws IOException {
		w.writeStructId(TRANSPORT_ACK);
		w.writeBytes(a.getId().getBytes());
		w.writeInt64(a.getVersion());
		if(flush) out.flush();
	}

	public void writeTransportUpdate(TransportUpdate u) throws IOException {
		w.writeStructId(TRANSPORT_UPDATE);
		w.writeBytes(u.getId().getBytes());
		w.writeMap(u.getProperties());
		w.writeInt64(u.getVersion());
		if(flush) out.flush();
	}

	public void flush() throws IOException {
		out.flush();
	}

	public void close() throws IOException {
		out.close();
	}
}

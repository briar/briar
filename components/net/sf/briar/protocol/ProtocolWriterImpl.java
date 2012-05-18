package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;

import java.io.IOException;
import java.io.OutputStream;
import java.util.BitSet;
import java.util.Map.Entry;

import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.ProtocolWriter;
import net.sf.briar.api.protocol.RawBatch;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.serial.SerialComponent;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

// This class is not thread-safe
class ProtocolWriterImpl implements ProtocolWriter {

	private final SerialComponent serial;
	private final OutputStream out;
	private final boolean flush;
	private final Writer w;

	ProtocolWriterImpl(SerialComponent serial, WriterFactory writerFactory,
			OutputStream out, boolean flush) {
		this.serial = serial;
		this.out = out;
		this.flush = flush;
		w = writerFactory.createWriter(out);
	}

	public int getMaxBatchesForAck(long capacity) {
		int packet = (int) Math.min(capacity, MAX_PACKET_LENGTH);
		int overhead = serial.getSerialisedStructIdLength(Types.ACK)
		+ serial.getSerialisedListStartLength()
		+ serial.getSerialisedListEndLength();
		int idLength = serial.getSerialisedUniqueIdLength();
		return (packet - overhead) / idLength;
	}

	public int getMaxMessagesForOffer(long capacity) {
		int packet = (int) Math.min(capacity, MAX_PACKET_LENGTH);
		int overhead = serial.getSerialisedStructIdLength(Types.OFFER)
		+ serial.getSerialisedListStartLength()
		+ serial.getSerialisedListEndLength();
		int idLength = serial.getSerialisedUniqueIdLength();
		return (packet - overhead) / idLength;
	}

	public int getMessageCapacityForBatch(long capacity) {
		int packet = (int) Math.min(capacity, MAX_PACKET_LENGTH);
		int overhead = serial.getSerialisedStructIdLength(Types.BATCH)
		+ serial.getSerialisedListStartLength()
		+ serial.getSerialisedListEndLength();
		return packet - overhead;
	}

	public void writeAck(Ack a) throws IOException {
		w.writeStructId(Types.ACK);
		w.writeListStart();
		for(BatchId b : a.getBatchIds()) w.writeBytes(b.getBytes());
		w.writeListEnd();
		if(flush) out.flush();
	}

	public void writeBatch(RawBatch b) throws IOException {
		w.writeStructId(Types.BATCH);
		w.writeListStart();
		for(byte[] raw : b.getMessages()) out.write(raw);
		w.writeListEnd();
		if(flush) out.flush();
	}

	public void writeOffer(Offer o) throws IOException {
		w.writeStructId(Types.OFFER);
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
		w.writeStructId(Types.REQUEST);
		w.writeUint7((byte) (bytes * 8 - length));
		w.writeBytes(bitmap);
		if(flush) out.flush();
	}

	public void writeSubscriptionUpdate(SubscriptionUpdate s)
	throws IOException {
		w.writeStructId(Types.SUBSCRIPTION_UPDATE);
		// Holes
		w.writeMapStart();
		for(Entry<GroupId, GroupId> e : s.getHoles().entrySet()) {
			w.writeBytes(e.getKey().getBytes());
			w.writeBytes(e.getValue().getBytes());
		}
		w.writeMapEnd();
		// Subscriptions
		w.writeMapStart();
		for(Entry<Group, Long> e : s.getSubscriptions().entrySet()) {
			writeGroup(w, e.getKey());
			w.writeInt64(e.getValue());
		}
		w.writeMapEnd();
		// Expiry time
		w.writeInt64(s.getExpiryTime());
		// Timestamp
		w.writeInt64(s.getTimestamp());
		if(flush) out.flush();
	}

	private void writeGroup(Writer w, Group g) throws IOException {
		w.writeStructId(Types.GROUP);
		w.writeString(g.getName());
		byte[] publicKey = g.getPublicKey();
		if(publicKey == null) w.writeNull();
		else w.writeBytes(publicKey);
	}

	public void writeTransportUpdate(TransportUpdate t) throws IOException {
		w.writeStructId(Types.TRANSPORT_UPDATE);
		w.writeListStart();
		for(Transport p : t.getTransports()) {
			w.writeStructId(Types.TRANSPORT);
			w.writeBytes(p.getId().getBytes());
			w.writeInt32(p.getIndex().getInt());
			w.writeMap(p);
		}
		w.writeListEnd();
		w.writeInt64(t.getTimestamp());
		if(flush) out.flush();
	}

	public void flush() throws IOException {
		out.flush();
	}

	public void close() throws IOException {
		out.close();
	}
}

package net.sf.briar.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageFactory;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.PacketFactory;
import net.sf.briar.api.protocol.ProtocolReader;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.ProtocolWriter;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.protocol.RawBatch;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.serial.SerialModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ProtocolReadWriteTest extends TestCase {

	private final ProtocolReaderFactory readerFactory;
	private final ProtocolWriterFactory writerFactory;
	private final PacketFactory packetFactory;
	private final BatchId batchId;
	private final Group group;
	private final Message message;
	private final String subject = "Hello";
	private final String messageBody = "Hello world";
	private final BitSet bitSet;
	private final Map<Group, Long> subscriptions;
	private final Collection<Transport> transports;
	private final long timestamp = System.currentTimeMillis();

	public ProtocolReadWriteTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule(),
				new ProtocolModule(), new SerialModule());
		readerFactory = i.getInstance(ProtocolReaderFactory.class);
		writerFactory = i.getInstance(ProtocolWriterFactory.class);
		packetFactory = i.getInstance(PacketFactory.class);
		batchId = new BatchId(TestUtils.getRandomId());
		GroupFactory groupFactory = i.getInstance(GroupFactory.class);
		group = groupFactory.createGroup("Unrestricted group", null);
		MessageFactory messageFactory = i.getInstance(MessageFactory.class);
		message = messageFactory.createMessage(null, group, subject,
				messageBody.getBytes("UTF-8"));
		bitSet = new BitSet();
		bitSet.set(3);
		bitSet.set(7);
		subscriptions = Collections.singletonMap(group, 123L);
		TransportId transportId = new TransportId(TestUtils.getRandomId());
		TransportIndex transportIndex = new TransportIndex(13);
		Transport transport = new Transport(transportId, transportIndex,
				Collections.singletonMap("bar", "baz"));
		transports = Collections.singletonList(transport);
	}

	@Test
	public void testWriteAndRead() throws Exception {
		// Write
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ProtocolWriter writer = writerFactory.createProtocolWriter(out);

		Ack a = packetFactory.createAck(Collections.singletonList(batchId));
		writer.writeAck(a);

		RawBatch b = packetFactory.createBatch(Collections.singletonList(
				message.getSerialised()));
		writer.writeBatch(b);

		Offer o = packetFactory.createOffer(Collections.singletonList(
				message.getId()));
		writer.writeOffer(o);

		Request r = packetFactory.createRequest(bitSet, 10);
		writer.writeRequest(r);

		SubscriptionUpdate s = packetFactory.createSubscriptionUpdate(
				subscriptions, timestamp);
		writer.writeSubscriptionUpdate(s);

		TransportUpdate t = packetFactory.createTransportUpdate(transports,
				timestamp);
		writer.writeTransportUpdate(t);

		// Read
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		ProtocolReader reader = readerFactory.createProtocolReader(in);

		a = reader.readAck();
		assertEquals(Collections.singletonList(batchId), a.getBatchIds());

		Batch b1 = reader.readBatch().verify();
		assertEquals(Collections.singletonList(message), b1.getMessages());

		o = reader.readOffer();
		assertEquals(Collections.singletonList(message.getId()),
				o.getMessageIds());

		r = reader.readRequest();
		assertEquals(bitSet, r.getBitmap());
		assertEquals(10, r.getLength());

		s = reader.readSubscriptionUpdate();
		assertEquals(subscriptions, s.getSubscriptions());
		assertEquals(timestamp, s.getTimestamp());

		t = reader.readTransportUpdate();
		assertEquals(transports, t.getTransports());
		assertEquals(timestamp, t.getTimestamp());
	}
}

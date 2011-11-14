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
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.ProtocolReader;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.protocol.writers.MessageEncoder;
import net.sf.briar.api.protocol.writers.OfferWriter;
import net.sf.briar.api.protocol.writers.ProtocolWriterFactory;
import net.sf.briar.api.protocol.writers.RequestWriter;
import net.sf.briar.api.protocol.writers.SubscriptionWriter;
import net.sf.briar.api.protocol.writers.TransportWriter;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.protocol.writers.ProtocolWritersModule;
import net.sf.briar.serial.SerialModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ProtocolReadWriteTest extends TestCase {

	private final ProtocolReaderFactory readerFactory;
	private final ProtocolWriterFactory writerFactory;
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
				new ProtocolModule(), new ProtocolWritersModule(),
				new SerialModule());
		readerFactory = i.getInstance(ProtocolReaderFactory.class);
		writerFactory = i.getInstance(ProtocolWriterFactory.class);
		batchId = new BatchId(TestUtils.getRandomId());
		GroupFactory groupFactory = i.getInstance(GroupFactory.class);
		group = groupFactory.createGroup("Unrestricted group", null);
		MessageEncoder messageEncoder = i.getInstance(MessageEncoder.class);
		message = messageEncoder.encodeMessage(null, group, subject,
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

		AckWriter a = writerFactory.createAckWriter(out);
		a.writeBatchId(batchId);
		a.finish();

		BatchWriter b = writerFactory.createBatchWriter(out);
		b.writeMessage(message.getSerialised());
		b.finish();

		OfferWriter o = writerFactory.createOfferWriter(out);
		o.writeMessageId(message.getId());
		o.finish();

		RequestWriter r = writerFactory.createRequestWriter(out);
		r.writeRequest(bitSet, 10);

		SubscriptionWriter s = writerFactory.createSubscriptionWriter(out);
		s.writeSubscriptions(subscriptions, timestamp);

		TransportWriter t = writerFactory.createTransportWriter(out);
		t.writeTransports(transports, timestamp);

		// Read
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		ProtocolReader reader = readerFactory.createProtocolReader(in);

		Ack ack = reader.readAck();
		assertEquals(Collections.singletonList(batchId), ack.getBatchIds());

		Batch batch = reader.readBatch();
		assertEquals(Collections.singletonList(message), batch.getMessages());

		Offer offer = reader.readOffer();
		assertEquals(Collections.singletonList(message.getId()),
				offer.getMessageIds());

		Request request = reader.readRequest();
		assertEquals(bitSet, request.getBitmap());

		SubscriptionUpdate subscriptionUpdate = reader.readSubscriptionUpdate();
		assertEquals(subscriptions, subscriptionUpdate.getSubscriptions());
		assertTrue(subscriptionUpdate.getTimestamp() == timestamp);

		TransportUpdate transportUpdate = reader.readTransportUpdate();
		assertEquals(transports, transportUpdate.getTransports());
		assertTrue(transportUpdate.getTimestamp() == timestamp);
	}
}

package net.sf.briar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import junit.framework.TestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorFactory;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageEncoder;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.ProtocolReader;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.protocol.writers.OfferWriter;
import net.sf.briar.api.protocol.writers.ProtocolWriterFactory;
import net.sf.briar.api.protocol.writers.RequestWriter;
import net.sf.briar.api.protocol.writers.SubscriptionWriter;
import net.sf.briar.api.protocol.writers.TransportWriter;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.db.DatabaseModule;
import net.sf.briar.protocol.ProtocolModule;
import net.sf.briar.protocol.writers.ProtocolWritersModule;
import net.sf.briar.serial.SerialModule;
import net.sf.briar.transport.TransportModule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class FileReadWriteTest extends TestCase {

	private final File testDir = TestUtils.getTestDirectory();
	private final File file = new File(testDir, "foo");

	private final BatchId ack = new BatchId(TestUtils.getRandomId());
	private final long timestamp = System.currentTimeMillis();

	private final ConnectionReaderFactory connectionReaderFactory;
	private final ConnectionWriterFactory connectionWriterFactory;
	private final ProtocolReaderFactory protocolReaderFactory;
	private final ProtocolWriterFactory protocolWriterFactory;
	private final CryptoComponent crypto;
	private final byte[] aliceSecret, bobSecret;
	private final int transportId = 123;
	private final long connection = 234L;
	private final Author author;
	private final Group group, group1;
	private final Message message, message1, message2, message3;
	private final String authorName = "Alice";
	private final String messageBody = "Hello world";
	private final Map<String, Map<String, String>> transports;

	public FileReadWriteTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule(),
				new DatabaseModule(), new ProtocolModule(),
				new ProtocolWritersModule(), new SerialModule(),
				new TestDatabaseModule(testDir), new TransportModule());
		connectionReaderFactory = i.getInstance(ConnectionReaderFactory.class);
		connectionWriterFactory = i.getInstance(ConnectionWriterFactory.class);
		protocolReaderFactory = i.getInstance(ProtocolReaderFactory.class);
		protocolWriterFactory = i.getInstance(ProtocolWriterFactory.class);
		crypto = i.getInstance(CryptoComponent.class);
		assertEquals(crypto.getMessageDigest().getDigestLength(),
				UniqueId.LENGTH);
		// Create matching secrets: one for Alice, one for Bob
		aliceSecret = new byte[45];
		aliceSecret[16] = (byte) 1;
		bobSecret = new byte[45];
		// Create two groups: one restricted, one unrestricted
		GroupFactory groupFactory = i.getInstance(GroupFactory.class);
		group = groupFactory.createGroup("Unrestricted group", null);
		KeyPair groupKeyPair = crypto.generateKeyPair();
		group1 = groupFactory.createGroup("Restricted group",
				groupKeyPair.getPublic().getEncoded());
		// Create an author
		AuthorFactory authorFactory = i.getInstance(AuthorFactory.class);
		KeyPair authorKeyPair = crypto.generateKeyPair();
		author = authorFactory.createAuthor(authorName,
				authorKeyPair.getPublic().getEncoded());
		// Create two messages to each group: one anonymous, one pseudonymous
		MessageEncoder messageEncoder = i.getInstance(MessageEncoder.class);
		message = messageEncoder.encodeMessage(null, group,
				messageBody.getBytes("UTF-8"));
		message1 = messageEncoder.encodeMessage(null, group1,
				groupKeyPair.getPrivate(), messageBody.getBytes("UTF-8"));
		message2 = messageEncoder.encodeMessage(null, group, author,
				authorKeyPair.getPrivate(), messageBody.getBytes("UTF-8"));
		message3 = messageEncoder.encodeMessage(null, group1,
				groupKeyPair.getPrivate(), author, authorKeyPair.getPrivate(),
				messageBody.getBytes("UTF-8"));
		transports = Collections.singletonMap("foo",
				Collections.singletonMap("bar", "baz"));
	}

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testWriteFile() throws Exception {
		OutputStream out = new FileOutputStream(file);
		// Use Alice's secret for writing
		ConnectionWriter w = connectionWriterFactory.createConnectionWriter(out,
				Long.MAX_VALUE, true, transportId, connection, aliceSecret);
		out = w.getOutputStream();

		AckWriter a = protocolWriterFactory.createAckWriter(out);
		assertTrue(a.writeBatchId(ack));
		a.finish();

		BatchWriter b = protocolWriterFactory.createBatchWriter(out);
		assertTrue(b.writeMessage(message.getBytes()));
		assertTrue(b.writeMessage(message1.getBytes()));
		assertTrue(b.writeMessage(message2.getBytes()));
		assertTrue(b.writeMessage(message3.getBytes()));
		b.finish();

		OfferWriter o = protocolWriterFactory.createOfferWriter(out);
		assertTrue(o.writeMessageId(message.getId()));
		assertTrue(o.writeMessageId(message1.getId()));
		assertTrue(o.writeMessageId(message2.getId()));
		assertTrue(o.writeMessageId(message3.getId()));
		o.finish();

		RequestWriter r = protocolWriterFactory.createRequestWriter(out);
		BitSet requested = new BitSet(4);
		requested.set(1);
		requested.set(3);
		r.writeRequest(requested, 4);

		SubscriptionWriter s =
			protocolWriterFactory.createSubscriptionWriter(out);
		// Use a LinkedHashMap for predictable iteration order
		Map<Group, Long> subs = new LinkedHashMap<Group, Long>();
		subs.put(group, 0L);
		subs.put(group1, 0L);
		s.writeSubscriptions(subs, timestamp);

		TransportWriter t = protocolWriterFactory.createTransportWriter(out);
		t.writeTransports(transports, timestamp);

		out.close();
		assertTrue(file.exists());
		assertTrue(file.length() > message.getSize());
	}

	@Test
	public void testWriteAndReadFile() throws Exception {

		testWriteFile();

		InputStream in = new FileInputStream(file);
		byte[] iv = new byte[16];
		int offset = 0;
		while(offset < 16) {
			int read = in.read(iv, offset, iv.length - offset);
			if(read == -1) break;
			offset += read;
		}
		assertEquals(16, offset);
		// Use Bob's secret for reading
		ConnectionReader r = connectionReaderFactory.createConnectionReader(in,
				iv, bobSecret);
		in = r.getInputStream();
		ProtocolReader protocolReader =
			protocolReaderFactory.createProtocolReader(in);

		// Read the ack
		assertTrue(protocolReader.hasAck());
		Ack a = protocolReader.readAck();
		assertEquals(Collections.singletonList(ack), a.getBatchIds());

		// Read the batch
		assertTrue(protocolReader.hasBatch());
		Batch b = protocolReader.readBatch();
		Collection<Message> messages = b.getMessages();
		assertEquals(4, messages.size());
		Iterator<Message> it = messages.iterator();
		checkMessageEquality(message, it.next());
		checkMessageEquality(message1, it.next());
		checkMessageEquality(message2, it.next());
		checkMessageEquality(message3, it.next());

		// Read the offer
		assertTrue(protocolReader.hasOffer());
		Offer o = protocolReader.readOffer();
		Collection<MessageId> offered = o.getMessageIds();
		assertEquals(4, offered.size());
		Iterator<MessageId> it1 = offered.iterator();
		assertEquals(message.getId(), it1.next());
		assertEquals(message1.getId(), it1.next());
		assertEquals(message2.getId(), it1.next());
		assertEquals(message3.getId(), it1.next());

		// Read the request
		assertTrue(protocolReader.hasRequest());
		Request req = protocolReader.readRequest();
		BitSet requested = req.getBitmap();
		assertFalse(requested.get(0));
		assertTrue(requested.get(1));
		assertFalse(requested.get(2));
		assertTrue(requested.get(3));
		// If there are any padding bits, they should all be zero
		assertEquals(2, requested.cardinality());

		// Read the subscription update
		assertTrue(protocolReader.hasSubscriptionUpdate());
		SubscriptionUpdate s = protocolReader.readSubscriptionUpdate();
		Map<Group, Long> subs = s.getSubscriptions();
		assertEquals(2, subs.size());
		assertEquals(Long.valueOf(0L), subs.get(group));
		assertEquals(Long.valueOf(0L), subs.get(group1));
		assertTrue(s.getTimestamp() == timestamp);

		// Read the transport update
		assertTrue(protocolReader.hasTransportUpdate());
		TransportUpdate t = protocolReader.readTransportUpdate();
		assertEquals(transports, t.getTransports());
		assertTrue(t.getTimestamp() == timestamp);

		in.close();
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}

	private void checkMessageEquality(Message m1, Message m2) {
		assertEquals(m1.getId(), m2.getId());
		assertEquals(m1.getParent(), m2.getParent());
		assertEquals(m1.getGroup(), m2.getGroup());
		assertEquals(m1.getAuthor(), m2.getAuthor());
		assertEquals(m1.getTimestamp(), m2.getTimestamp());
		assertTrue(Arrays.equals(m1.getBytes(), m2.getBytes()));
	}
}

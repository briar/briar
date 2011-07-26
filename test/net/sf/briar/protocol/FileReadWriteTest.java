package net.sf.briar.protocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
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
import net.sf.briar.api.protocol.Subscriptions;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.Transports;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.protocol.writers.OfferWriter;
import net.sf.briar.api.protocol.writers.PacketWriterFactory;
import net.sf.briar.api.protocol.writers.SubscriptionWriter;
import net.sf.briar.api.protocol.writers.TransportWriter;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.protocol.writers.WritersModule;
import net.sf.briar.serial.SerialModule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class FileReadWriteTest extends TestCase {

	private final File testDir = TestUtils.getTestDirectory();
	private final File file = new File(testDir, "foo");

	private final BatchId ack = new BatchId(TestUtils.getRandomId());
	private final long start = System.currentTimeMillis();

	private final ReaderFactory readerFactory;
	private final PacketWriterFactory packetWriterFactory;
	private final CryptoComponent crypto;
	private final AckReader ackReader;
	private final BatchReader batchReader;
	private final OfferReader offerReader;
	private final SubscriptionReader subscriptionReader;
	private final TransportReader transportReader;
	private final Author author;
	private final Group group, group1;
	private final Message message, message1, message2, message3;
	private final String authorName = "Alice";
	private final String messageBody = "Hello world";

	public FileReadWriteTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule(),
				new ProtocolModule(), new SerialModule(),
				new WritersModule());
		readerFactory = i.getInstance(ReaderFactory.class);
		packetWriterFactory = i.getInstance(PacketWriterFactory.class);
		crypto = i.getInstance(CryptoComponent.class);
		assertEquals(crypto.getMessageDigest().getDigestLength(),
				UniqueId.LENGTH);
		ackReader = i.getInstance(AckReader.class);
		batchReader = i.getInstance(BatchReader.class);
		offerReader = i.getInstance(OfferReader.class);
		subscriptionReader = i.getInstance(SubscriptionReader.class);
		transportReader = i.getInstance(TransportReader.class);
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
		message = messageEncoder.encodeMessage(MessageId.NONE, group,
				messageBody.getBytes("UTF-8"));
		message1 = messageEncoder.encodeMessage(MessageId.NONE, group1,
				groupKeyPair.getPrivate(), messageBody.getBytes("UTF-8"));
		message2 = messageEncoder.encodeMessage(MessageId.NONE, group, author,
				authorKeyPair.getPrivate(), messageBody.getBytes("UTF-8"));
		message3 = messageEncoder.encodeMessage(MessageId.NONE, group1,
				groupKeyPair.getPrivate(), author, authorKeyPair.getPrivate(),
				messageBody.getBytes("UTF-8"));
	}

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testWriteFile() throws Exception {
		FileOutputStream out = new FileOutputStream(file);

		AckWriter a = packetWriterFactory.createAckWriter(out);
		assertTrue(a.writeBatchId(ack));
		a.finish();

		BatchWriter b = packetWriterFactory.createBatchWriter(out);
		assertTrue(b.writeMessage(message.getBytes()));
		assertTrue(b.writeMessage(message1.getBytes()));
		assertTrue(b.writeMessage(message2.getBytes()));
		assertTrue(b.writeMessage(message3.getBytes()));
		b.finish();

		OfferWriter o = packetWriterFactory.createOfferWriter(out);
		assertTrue(o.writeMessageId(message.getId()));
		assertTrue(o.writeMessageId(message1.getId()));
		assertTrue(o.writeMessageId(message2.getId()));
		assertTrue(o.writeMessageId(message3.getId()));
		o.finish();

		SubscriptionWriter s =
			packetWriterFactory.createSubscriptionWriter(out);
		Collection<Group> subs = new ArrayList<Group>();
		subs.add(group);
		subs.add(group1);
		s.writeSubscriptions(subs);

		TransportWriter t = packetWriterFactory.createTransportWriter(out);
		t.writeTransports(Collections.singletonMap("foo", "bar"));

		out.close();
		assertTrue(file.exists());
		assertTrue(file.length() > message.getSize());
	}

	@Test
	public void testWriteAndReadFile() throws Exception {

		testWriteFile();

		FileInputStream in = new FileInputStream(file);
		Reader reader = readerFactory.createReader(in);
		reader.addObjectReader(Tags.ACK, ackReader);
		reader.addObjectReader(Tags.BATCH, batchReader);
		reader.addObjectReader(Tags.OFFER, offerReader);
		reader.addObjectReader(Tags.SUBSCRIPTIONS, subscriptionReader);
		reader.addObjectReader(Tags.TRANSPORTS, transportReader);

		// Read the ack
		assertTrue(reader.hasUserDefined(Tags.ACK));
		Ack a = reader.readUserDefined(Tags.ACK, Ack.class);
		assertEquals(Collections.singletonList(ack), a.getBatches());

		// Read the batch
		assertTrue(reader.hasUserDefined(Tags.BATCH));
		Batch b = reader.readUserDefined(Tags.BATCH, Batch.class);
		Collection<Message> messages = b.getMessages();
		assertEquals(4, messages.size());
		Iterator<Message> i = messages.iterator();
		checkMessageEquality(message, i.next());
		checkMessageEquality(message1, i.next());
		checkMessageEquality(message2, i.next());
		checkMessageEquality(message3, i.next());

		// Read the offer
		assertTrue(reader.hasUserDefined(Tags.OFFER));
		Offer o = reader.readUserDefined(Tags.OFFER, Offer.class);
		Collection<MessageId> ids = o.getMessages();
		assertEquals(4, ids.size());
		Iterator<MessageId> i1 = ids.iterator();
		assertEquals(message.getId(), i1.next());
		assertEquals(message1.getId(), i1.next());
		assertEquals(message2.getId(), i1.next());
		assertEquals(message3.getId(), i1.next());

		// Read the subscriptions update
		assertTrue(reader.hasUserDefined(Tags.SUBSCRIPTIONS));
		Subscriptions s = reader.readUserDefined(Tags.SUBSCRIPTIONS,
				Subscriptions.class);
		Collection<Group> subs = s.getSubscriptions();
		assertEquals(2, subs.size());
		Iterator<Group> i2 = subs.iterator();
		checkGroupEquality(group, i2.next());
		checkGroupEquality(group1, i2.next());
		assertTrue(s.getTimestamp() > start);
		assertTrue(s.getTimestamp() <= System.currentTimeMillis());

		// Read the transports update
		assertTrue(reader.hasUserDefined(Tags.TRANSPORTS));
		Transports t = reader.readUserDefined(Tags.TRANSPORTS,
				Transports.class);
		assertEquals(Collections.singletonMap("foo", "bar"), t.getTransports());
		assertTrue(t.getTimestamp() > start);
		assertTrue(t.getTimestamp() <= System.currentTimeMillis());

		assertTrue(reader.eof());
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

	private void checkGroupEquality(Group g1, Group g2) {
		assertEquals(g1.getId(), g2.getId());
		assertEquals(g1.getName(), g2.getName());
		byte[] k1 = g1.getPublicKey();
		byte[] k2 = g2.getPublicKey();
		if(k1 == null) assertNull(k2);
		else assertTrue(Arrays.equals(k1, k2));
	}
}

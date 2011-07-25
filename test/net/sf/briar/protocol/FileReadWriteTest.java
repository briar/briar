package net.sf.briar.protocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyPair;
import java.util.Arrays;
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
import net.sf.briar.api.protocol.Subscriptions;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.Transports;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.protocol.writers.PacketWriterFactory;
import net.sf.briar.api.protocol.writers.SubscriptionWriter;
import net.sf.briar.api.protocol.writers.TransportWriter;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.WriterFactory;
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
	private final String authorName = "Foo Bar";
	private final String messageBody = "This is the message body! Wooooooo!";
	private final long start = System.currentTimeMillis();

	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;
	private final PacketWriterFactory packetWriterFactory;
	private final CryptoComponent crypto;
	private final Author author;
	private final Group group;
	private final Message message;

	public FileReadWriteTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule(),
				new ProtocolModule(), new SerialModule(),
				new WritersModule());
		readerFactory = i.getInstance(ReaderFactory.class);
		writerFactory = i.getInstance(WriterFactory.class);
		packetWriterFactory = i.getInstance(PacketWriterFactory.class);
		crypto = i.getInstance(CryptoComponent.class);
		assertEquals(crypto.getMessageDigest().getDigestLength(),
				UniqueId.LENGTH);
		// Create a group
		GroupFactory groupFactory = i.getInstance(GroupFactory.class);
		group = groupFactory.createGroup("Group name", null);
		// Create an author
		AuthorFactory authorFactory = i.getInstance(AuthorFactory.class);
		KeyPair keyPair = crypto.generateKeyPair();
		author = authorFactory.createAuthor(authorName,
				keyPair.getPublic().getEncoded());
		// Create and encode a test message, signed by the author
		MessageEncoder messageEncoder = i.getInstance(MessageEncoder.class);
		message = messageEncoder.encodeMessage(MessageId.NONE, group, author,
				keyPair.getPrivate(), messageBody.getBytes("UTF-8"));
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
		b.finish();

		SubscriptionWriter s =
			packetWriterFactory.createSubscriptionWriter(out);
		s.writeSubscriptions(Collections.singleton(group));

		TransportWriter t = packetWriterFactory.createTransportWriter(out);
		t.writeTransports(Collections.singletonMap("foo", "bar"));

		out.close();
		assertTrue(file.exists());
		assertTrue(file.length() > message.getSize());
	}

	@Test
	public void testWriteAndReadFile() throws Exception {

		testWriteFile();

		GroupReader groupReader = new GroupReader(crypto,
				new GroupFactoryImpl(crypto, writerFactory));
		AuthorReader authorReader = new AuthorReader(crypto,
				new AuthorFactoryImpl(crypto, writerFactory));
		MessageReader messageReader = new MessageReader(crypto, groupReader,
				authorReader);
		AckReader ackReader = new AckReader(new BatchIdReader(),
				new AckFactoryImpl());
		BatchReader batchReader = new BatchReader(crypto, messageReader,
				new BatchFactoryImpl());
		SubscriptionReader subscriptionReader =
			new SubscriptionReader(groupReader, new SubscriptionFactoryImpl());
		TransportReader transportReader =
			new TransportReader(new TransportFactoryImpl());

		FileInputStream in = new FileInputStream(file);
		Reader reader = readerFactory.createReader(in);
		reader.addObjectReader(Tags.ACK, ackReader);
		reader.addObjectReader(Tags.BATCH, batchReader);
		reader.addObjectReader(Tags.SUBSCRIPTIONS, subscriptionReader);
		reader.addObjectReader(Tags.TRANSPORTS, transportReader);

		// Read the ack
		assertTrue(reader.hasUserDefined(Tags.ACK));
		Ack a = reader.readUserDefined(Tags.ACK, Ack.class);
		assertEquals(Collections.singletonList(ack), a.getBatches());

		// Read the batch
		assertTrue(reader.hasUserDefined(Tags.BATCH));
		Batch b = reader.readUserDefined(Tags.BATCH, Batch.class);
		Iterator<Message> i = b.getMessages().iterator();
		assertTrue(i.hasNext());
		Message m = i.next();
		assertEquals(message.getId(), m.getId());
		assertEquals(message.getParent(), m.getParent());
		assertEquals(message.getGroup(), m.getGroup());
		assertEquals(message.getAuthor(), m.getAuthor());
		assertEquals(message.getTimestamp(), m.getTimestamp());
		assertTrue(Arrays.equals(message.getBytes(), m.getBytes()));
		assertFalse(i.hasNext());

		// Read the subscriptions update
		assertTrue(reader.hasUserDefined(Tags.SUBSCRIPTIONS));
		Subscriptions s = reader.readUserDefined(Tags.SUBSCRIPTIONS,
				Subscriptions.class);
		assertEquals(Collections.singletonList(group), s.getSubscriptions());
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
}

package net.sf.briar.protocol;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.GroupId;
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
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.protocol.writers.WritersModule;
import net.sf.briar.serial.SerialModule;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class FileReadWriteTest extends TestCase {

	private final File testDir = TestUtils.getTestDirectory();
	private final File file = new File(testDir, "foo");

	private final BatchId ack = new BatchId(TestUtils.getRandomId());
	private final GroupId sub = new GroupId(TestUtils.getRandomId());
	private final String nick = "Foo Bar";
	private final String messageBody = "This is the message body! Wooooooo!";
	private final long start = System.currentTimeMillis();

	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;
	private final PacketWriterFactory packetWriterFactory;
	private final Signature signature;
	private final MessageDigest messageDigest, batchDigest;
	private final KeyParser keyParser;
	private final Message message;
	private final Group group;

	public FileReadWriteTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule(),
				new ProtocolModule(), new SerialModule(),
				new WritersModule());
		readerFactory = i.getInstance(ReaderFactory.class);
		writerFactory = i.getInstance(WriterFactory.class);
		packetWriterFactory = i.getInstance(PacketWriterFactory.class);
		keyParser = i.getInstance(KeyParser.class);
		signature = i.getInstance(Signature.class);
		messageDigest = i.getInstance(MessageDigest.class);
		batchDigest = i.getInstance(MessageDigest.class);
		assertEquals(messageDigest.getDigestLength(), UniqueId.LENGTH);
		assertEquals(batchDigest.getDigestLength(), UniqueId.LENGTH);
		// Create and encode a test message
		MessageEncoder messageEncoder = i.getInstance(MessageEncoder.class);
		KeyPair keyPair = i.getInstance(KeyPair.class);
		message = messageEncoder.encodeMessage(MessageId.NONE, sub, nick,
				keyPair, messageBody.getBytes("UTF-8"));
		// Create a test group, then write and read it to calculate its ID
		GroupFactory groupFactory = i.getInstance(GroupFactory.class);
		Group noId = groupFactory.createGroup(
				new GroupId(new byte[UniqueId.LENGTH]), "Group name", false,
				TestUtils.getRandomId());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		noId.writeTo(w);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		Reader r = readerFactory.createReader(in);
		ObjectReader<Group> groupReader = new GroupReader(batchDigest,
				groupFactory);
		group = groupReader.readObject(r);
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

		MessageReader messageReader =
			new MessageReader(keyParser, signature, messageDigest);
		ObjectReader<Ack> ackReader = new AckReader(new BatchIdReader(),
				new AckFactoryImpl());
		ObjectReader<Batch> batchReader = new BatchReader(batchDigest,
				messageReader, new BatchFactoryImpl());
		ObjectReader<Group> groupReader = new GroupReader(batchDigest,
				new GroupFactoryImpl(keyParser));
		ObjectReader<Subscriptions> subscriptionReader =
			new SubscriptionReader(groupReader, new SubscriptionFactoryImpl());
		ObjectReader<Transports> transportReader =
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

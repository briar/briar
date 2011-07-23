package net.sf.briar.protocol;

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
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageEncoder;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.protocol.writers.PacketWriterFactory;
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
	private final GroupId sub = new GroupId(TestUtils.getRandomId());
	private final String nick = "Foo Bar";
	private final String messageBody = "This is the message body! Wooooooo!";

	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;
	private final PacketWriterFactory packetWriterFactory;
	private final Signature signature;
	private final MessageDigest messageDigest, batchDigest;
	private final KeyParser keyParser;
	private final Message message;

	public FileReadWriteTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new SerialModule(),
				new CryptoModule(), new WritersModule());
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
		MessageEncoder messageEncoder = new MessageEncoderImpl(signature,
				messageDigest, writerFactory);
		KeyPair keyPair = i.getInstance(KeyPair.class);
		message = messageEncoder.encodeMessage(MessageId.NONE, sub, nick,
				keyPair, messageBody.getBytes("UTF-8"));
	}

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testWriteFile() throws Exception {
		FileOutputStream out = new FileOutputStream(file);

		AckWriter a = packetWriterFactory.createAckWriter(out);
		a.addBatchId(ack);
		a.finish();

		BatchWriter b = packetWriterFactory.createBatchWriter(out);
		b.addMessage(message.getBytes());
		b.finish();

		out.close();
		assertTrue(file.exists());
		assertTrue(file.length() > message.getSize());
	}

	@Test
	public void testWriteAndReadFile() throws Exception {

		testWriteFile();

		MessageReader messageReader =
			new MessageReader(keyParser, signature, messageDigest);
		AckReader ackReader = new AckReader(new AckFactoryImpl());
		BatchReader batchReader = new BatchReader(batchDigest, messageReader,
				new BatchFactoryImpl());
		FileInputStream in = new FileInputStream(file);
		Reader reader = readerFactory.createReader(in);
		reader.addObjectReader(Tags.ACK, ackReader);
		reader.addObjectReader(Tags.BATCH, batchReader);

		assertTrue(reader.hasUserDefined(Tags.ACK));
		Ack a = reader.readUserDefined(Tags.ACK, Ack.class);
		assertEquals(Collections.singletonList(ack), a.getBatches());

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

		assertTrue(reader.eof());
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}

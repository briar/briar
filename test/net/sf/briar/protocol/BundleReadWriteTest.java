package net.sf.briar.protocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BundleReader;
import net.sf.briar.api.protocol.BundleWriter;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageEncoder;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.Raw;
import net.sf.briar.api.serial.RawByteArray;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.serial.SerialModule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class BundleReadWriteTest extends TestCase {

	private static final String SIGNATURE_ALGO = "SHA256withRSA";
	private static final String KEY_PAIR_ALGO = "RSA";
	private static final String DIGEST_ALGO = "SHA-256";

	private final File testDir = TestUtils.getTestDirectory();
	private final File bundle = new File(testDir, "bundle");

	private final long capacity = 1024L;
	private final BatchId ack = new BatchId(TestUtils.getRandomId());
	private final Set<BatchId> acks = Collections.singleton(ack);
	private final GroupId sub = new GroupId(TestUtils.getRandomId());
	private final Set<GroupId> subs = Collections.singleton(sub);
	private final Map<String, String> transports =
		Collections.singletonMap("foo", "bar");
	private final String nick = "Foo Bar";
	private final String messageBody = "This is the message body! Wooooooo!";

	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;
	private final Signature signature;
	private final MessageDigest messageDigest, batchDigest;
	private final KeyParser keyParser;
	private final Message message;

	public BundleReadWriteTest() throws Exception {
		super();
		// Inject the reader and writer factories, since they belong to
		// a different component
		Injector i = Guice.createInjector(new SerialModule());
		readerFactory = i.getInstance(ReaderFactory.class);
		writerFactory = i.getInstance(WriterFactory.class);
		signature = Signature.getInstance(SIGNATURE_ALGO);
		messageDigest = MessageDigest.getInstance(DIGEST_ALGO);
		batchDigest = MessageDigest.getInstance(DIGEST_ALGO);
		final KeyFactory keyFactory = KeyFactory.getInstance(KEY_PAIR_ALGO);
		keyParser = new KeyParser() {
			public PublicKey parsePublicKey(byte[] encodedKey)
			throws InvalidKeySpecException {
				EncodedKeySpec e = new X509EncodedKeySpec(encodedKey);
				return keyFactory.generatePublic(e);
			}
		};
		assertEquals(messageDigest.getDigestLength(), UniqueId.LENGTH);
		assertEquals(batchDigest.getDigestLength(), UniqueId.LENGTH);
		// Create and encode a test message
		MessageEncoder messageEncoder = new MessageEncoderImpl(signature,
				messageDigest, writerFactory);
		KeyPair keyPair =
			KeyPairGenerator.getInstance(KEY_PAIR_ALGO).generateKeyPair();
		message = messageEncoder.encodeMessage(MessageId.NONE, sub, nick,
				keyPair, messageBody.getBytes("UTF-8"));
	}

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testWriteBundle() throws Exception {
		FileOutputStream out = new FileOutputStream(bundle);
		BundleWriter w = new BundleWriterImpl(out, writerFactory, batchDigest,
				capacity);
		Raw messageRaw = new RawByteArray(message.getBytes());

		w.addHeader(acks, subs, transports);
		w.addBatch(Collections.singleton(messageRaw));
		w.finish();

		assertTrue(bundle.exists());
		assertTrue(bundle.length() > message.getSize());
	}

	@Test
	public void testWriteAndReadBundle() throws Exception {

		testWriteBundle();

		FileInputStream in = new FileInputStream(bundle);
		Reader reader = readerFactory.createReader(in);
		MessageReader messageReader =
			new MessageReader(keyParser, signature, messageDigest);
		HeaderReader headerReader = new HeaderReader(new HeaderFactoryImpl());
		BatchReader batchReader = new BatchReader(batchDigest, messageReader,
				new BatchFactoryImpl());
		BundleReader r = new BundleReaderImpl(reader, headerReader,
				batchReader);

		Header h = r.getHeader();
		assertEquals(acks, h.getAcks());
		assertEquals(subs, h.getSubscriptions());
		assertEquals(transports, h.getTransports());
		Batch b = r.getNextBatch();
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
		assertNull(r.getNextBatch());
		r.finish();
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}

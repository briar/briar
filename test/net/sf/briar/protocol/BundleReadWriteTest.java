package net.sf.briar.protocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.security.GeneralSecurityException;
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

	private final ReaderFactory rf;
	private final WriterFactory wf;

	private final KeyPair keyPair;
	private final Signature sig, sig1;
	private final MessageDigest dig, dig1;
	private final KeyParser keyParser;
	private final Message message;

	public BundleReadWriteTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new SerialModule());
		rf = i.getInstance(ReaderFactory.class);
		wf = i.getInstance(WriterFactory.class);
		keyPair = KeyPairGenerator.getInstance(KEY_PAIR_ALGO).generateKeyPair();
		sig = Signature.getInstance(SIGNATURE_ALGO);
		sig1 = Signature.getInstance(SIGNATURE_ALGO);
		dig = MessageDigest.getInstance(DIGEST_ALGO);
		dig1 = MessageDigest.getInstance(DIGEST_ALGO);
		final KeyFactory keyFactory = KeyFactory.getInstance(KEY_PAIR_ALGO);
		keyParser = new KeyParser() {
			public PublicKey parsePublicKey(byte[] encodedKey)
			throws InvalidKeySpecException {
				EncodedKeySpec e = new X509EncodedKeySpec(encodedKey);
				return keyFactory.generatePublic(e);
			}
		};
		assertEquals(dig.getDigestLength(), UniqueId.LENGTH);
		MessageEncoder messageEncoder = new MessageEncoderImpl(sig, dig, wf);
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
		BundleWriter w = new BundleWriterImpl(out, wf, keyPair.getPrivate(),
				sig, dig, capacity);
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
		Reader reader = rf.createReader(in);
		MessageReader messageReader = new MessageReader(keyParser, sig1, dig1);
		HeaderReader headerReader = new HeaderReader(keyPair.getPublic(), sig,
				new HeaderFactoryImpl());
		BatchReader batchReader = new BatchReader(keyPair.getPublic(), sig, dig,
				messageReader, new BatchFactoryImpl());
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


	@Test
	public void testModifyingBundleBreaksSignature() throws Exception {

		testWriteBundle();

		RandomAccessFile f = new RandomAccessFile(bundle, "rw");
		f.seek(bundle.length() - 100);
		byte b = f.readByte();
		f.seek(bundle.length() - 100);
		f.writeByte(b + 1);
		f.close();

		FileInputStream in = new FileInputStream(bundle);
		Reader reader = rf.createReader(in);
		MessageReader messageReader = new MessageReader(keyParser, sig1, dig1);
		HeaderReader headerReader = new HeaderReader(keyPair.getPublic(), sig,
				new HeaderFactoryImpl());
		BatchReader batchReader = new BatchReader(keyPair.getPublic(), sig, dig,
				messageReader, new BatchFactoryImpl());
		BundleReader r = new BundleReaderImpl(reader, headerReader,
				batchReader);

		Header h = r.getHeader();
		assertEquals(acks, h.getAcks());
		assertEquals(subs, h.getSubscriptions());
		assertEquals(transports, h.getTransports());
		try {
			r.getNextBatch();
			assertTrue(false);
		} catch(GeneralSecurityException expected) {}
		r.finish();
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}

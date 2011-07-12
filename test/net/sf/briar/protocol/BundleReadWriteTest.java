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
import net.sf.briar.api.protocol.BatchBuilder;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BundleReader;
import net.sf.briar.api.protocol.BundleWriter;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;
import net.sf.briar.api.protocol.HeaderBuilder;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageEncoder;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.MessageParser;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.serial.ReaderFactoryImpl;
import net.sf.briar.serial.WriterFactoryImpl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Provider;

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

	// FIXME: This test should not depend on impls in another component
	private final ReaderFactory rf = new ReaderFactoryImpl();
	private final WriterFactory wf = new WriterFactoryImpl();

	private final KeyPair keyPair;
	private final Signature sig;
	private final MessageDigest digest;
	private final KeyParser keyParser;
	private final Message message;

	public BundleReadWriteTest() throws Exception {
		super();
		keyPair = KeyPairGenerator.getInstance(KEY_PAIR_ALGO).generateKeyPair();
		sig = Signature.getInstance(SIGNATURE_ALGO);
		digest = MessageDigest.getInstance(DIGEST_ALGO);
		keyParser = new KeyParser() {
			public PublicKey parsePublicKey(byte[] encodedKey) throws GeneralSecurityException {
				EncodedKeySpec e = new X509EncodedKeySpec(encodedKey);
				return KeyFactory.getInstance(KEY_PAIR_ALGO).generatePublic(e);
			}
		};
		assertEquals(digest.getDigestLength(), UniqueId.LENGTH);
		MessageEncoder messageEncoder = new MessageEncoderImpl(sig, digest, wf);
		message = messageEncoder.encodeMessage(MessageId.NONE, sub, nick,
				keyPair, messageBody.getBytes("UTF-8"));
	}

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testWriteBundle() throws Exception {
		HeaderBuilder h = new OutgoingHeaderBuilder(keyPair, sig, digest, wf);
		h.addAcks(acks);
		h.addSubscriptions(subs);
		h.addTransports(transports);
		Header header = h.build();

		BatchBuilder b = new OutgoingBatchBuilder(keyPair, sig, digest, wf);
		b.addMessage(message);
		Batch batch = b.build();

		FileOutputStream out = new FileOutputStream(bundle);
		Writer writer = new WriterFactoryImpl().createWriter(out);
		BundleWriter w = new BundleWriterImpl(writer, capacity);

		w.addHeader(header);
		w.addBatch(batch);
		w.close();

		assertTrue(bundle.exists());
		assertTrue(bundle.length() > message.getSize());
	}

	@Test
	public void testWriteAndReadBundle() throws Exception {

		testWriteBundle();

		MessageParser messageParser =
			new MessageParserImpl(keyParser, sig, digest, rf);
		Provider<HeaderBuilder> headerBuilderProvider =
			new Provider<HeaderBuilder>() {
			public HeaderBuilder get() {
				return new IncomingHeaderBuilder(keyPair, sig, digest, wf);
			}
		};
		Provider<BatchBuilder> batchBuilderProvider =
			new Provider<BatchBuilder>() {
			public BatchBuilder get() {
				return new IncomingBatchBuilder(keyPair, sig, digest, wf);
			}
		};

		FileInputStream in = new FileInputStream(bundle);
		Reader reader = new ReaderFactoryImpl().createReader(in);
		BundleReader r = new BundleReaderImpl(reader, bundle.length(),
				messageParser, headerBuilderProvider, batchBuilderProvider);

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
		r.close();
	}


	@Test
	public void testModifyingBundleBreaksSignature() throws Exception {

		testWriteBundle();

		RandomAccessFile f = new RandomAccessFile(bundle, "rw");
		f.seek(bundle.length() - 150);
		byte b = f.readByte();
		f.seek(bundle.length() - 150);
		f.writeByte(b + 1);
		f.close();

		MessageParser messageParser =
			new MessageParserImpl(keyParser, sig, digest, rf);
		Provider<HeaderBuilder> headerBuilderProvider =
			new Provider<HeaderBuilder>() {
			public HeaderBuilder get() {
				return new IncomingHeaderBuilder(keyPair, sig, digest, wf);
			}
		};
		Provider<BatchBuilder> batchBuilderProvider =
			new Provider<BatchBuilder>() {
			public BatchBuilder get() {
				return new IncomingBatchBuilder(keyPair, sig, digest, wf);
			}
		};

		FileInputStream in = new FileInputStream(bundle);
		Reader reader = new ReaderFactoryImpl().createReader(in);
		BundleReader r = new BundleReaderImpl(reader, bundle.length(),
				messageParser, headerBuilderProvider, batchBuilderProvider);

		Header h = r.getHeader();
		assertEquals(acks, h.getAcks());
		assertEquals(subs, h.getSubscriptions());
		assertEquals(transports, h.getTransports());
		try {
			r.getNextBatch();
			assertTrue(false);
		} catch(GeneralSecurityException expected) {}
		r.close();
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}

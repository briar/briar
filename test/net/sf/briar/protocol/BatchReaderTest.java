package net.sf.briar.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Collections;

import junit.framework.TestCase;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.FormatException;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.serial.SerialModule;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class BatchReaderTest extends TestCase {

	private static final String DIGEST_ALGO = "SHA-256";

	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;
	private final MessageDigest messageDigest;
	private final Mockery context;
	private final Message message;

	public BatchReaderTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new SerialModule());
		readerFactory = i.getInstance(ReaderFactory.class);
		writerFactory = i.getInstance(WriterFactory.class);
		messageDigest = MessageDigest.getInstance(DIGEST_ALGO);
		context = new Mockery();
		message = context.mock(Message.class);
	}

	@Before
	public void setUp() {
		messageDigest.reset();
	}

	@Test
	public void testFormatExceptionIfBatchIsTooLarge() throws Exception {
		ObjectReader<Message> messageReader = new TestMessageReader();
		BatchFactory batchFactory = context.mock(BatchFactory.class);
		BatchReader batchReader = new BatchReader(messageDigest, messageReader,
				batchFactory);

		byte[] b = createBatch(Batch.MAX_SIZE + 1);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addObjectReader(Tags.BATCH, batchReader);

		reader.readUserDefinedTag(Tags.BATCH);
		try {
			reader.readUserDefinedObject(Tags.BATCH, Batch.class);
			assertTrue(false);
		} catch(FormatException expected) {}
	}

	@Test
	public void testNoFormatExceptionIfBatchIsMaximumSize() throws Exception {
		ObjectReader<Message> messageReader = new TestMessageReader();
		final BatchFactory batchFactory = context.mock(BatchFactory.class);
		BatchReader batchReader = new BatchReader(messageDigest, messageReader,
				batchFactory);
		final Batch batch = context.mock(Batch.class);
		context.checking(new Expectations() {{
			oneOf(batchFactory).createBatch(with(any(BatchId.class)),
					with(Collections.singletonList(message)));
			will(returnValue(batch));
		}});

		byte[] b = createBatch(Batch.MAX_SIZE);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addObjectReader(Tags.BATCH, batchReader);

		reader.readUserDefinedTag(Tags.BATCH);
		assertEquals(batch, reader.readUserDefinedObject(Tags.BATCH,
				Batch.class));
	}

	@Test
	public void testBatchId() throws Exception {
		byte[] b = createBatch(Batch.MAX_SIZE);
		// Calculate the expected batch ID, skipping the initial tag
		messageDigest.update(b, 1, b.length - 1);
		final BatchId id = new BatchId(messageDigest.digest());
		messageDigest.reset();

		ObjectReader<Message> messageReader = new TestMessageReader();
		final BatchFactory batchFactory = context.mock(BatchFactory.class);
		BatchReader batchReader = new BatchReader(messageDigest, messageReader,
				batchFactory);
		final Batch batch = context.mock(Batch.class);
		context.checking(new Expectations() {{
			// Check that the batch ID matches the expected ID
			oneOf(batchFactory).createBatch(with(id),
					with(Collections.singletonList(message)));
			will(returnValue(batch));
		}});

		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addObjectReader(Tags.BATCH, batchReader);

		reader.readUserDefinedTag(Tags.BATCH);
		assertEquals(batch, reader.readUserDefinedObject(Tags.BATCH,
				Batch.class));
	}

	@Test
	public void testEmptyBatch() throws Exception {
		ObjectReader<Message> messageReader = new TestMessageReader();
		final BatchFactory batchFactory = context.mock(BatchFactory.class);
		BatchReader batchReader = new BatchReader(messageDigest, messageReader,
				batchFactory);
		final Batch batch = context.mock(Batch.class);
		context.checking(new Expectations() {{
			oneOf(batchFactory).createBatch(with(any(BatchId.class)),
					with(Collections.<Message>emptyList()));
			will(returnValue(batch));
		}});

		byte[] b = createEmptyBatch();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addObjectReader(Tags.BATCH, batchReader);

		reader.readUserDefinedTag(Tags.BATCH);
		assertEquals(batch, reader.readUserDefinedObject(Tags.BATCH,
				Batch.class));
	}

	private byte[] createBatch(int size) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream(size);
		Writer w = writerFactory.createWriter(out);
		w.writeUserDefinedTag(Tags.BATCH);
		w.writeListStart();
		w.writeUserDefinedTag(Tags.MESSAGE);
		// We're using a fake message reader, so it's OK to use a fake message
		w.writeRaw(new byte[size - 10]);
		w.writeListEnd();
		w.close();
		byte[] b = out.toByteArray();
		assertEquals(size, b.length);
		return b;
	}

	private byte[] createEmptyBatch() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeUserDefinedTag(Tags.BATCH);
		w.writeListStart();
		w.writeListEnd();
		w.close();
		return out.toByteArray();
	}

	private class TestMessageReader implements ObjectReader<Message> {

		public Message readObject(Reader r) throws IOException,
		GeneralSecurityException {
			r.readRaw();
			return message;
		}
	}
}

package net.sf.briar.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;

import junit.framework.TestCase;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.UnverifiedBatch;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.serial.SerialModule;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class BatchReaderTest extends TestCase {

	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;
	private final CryptoComponent crypto;
	private final Mockery context;
	private final UnverifiedMessage message;
	private final ObjectReader<UnverifiedMessage> messageReader;

	public BatchReaderTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new SerialModule(),
				new CryptoModule());
		readerFactory = i.getInstance(ReaderFactory.class);
		writerFactory = i.getInstance(WriterFactory.class);
		crypto = i.getInstance(CryptoComponent.class);
		context = new Mockery();
		message = context.mock(UnverifiedMessage.class);
		messageReader = new TestMessageReader();
	}

	@Test
	public void testFormatExceptionIfBatchIsTooLarge() throws Exception {
		UnverifiedBatchFactory batchFactory =
			context.mock(UnverifiedBatchFactory.class);
		BatchReader batchReader = new BatchReader(crypto, messageReader,
				batchFactory);

		byte[] b = createBatch(ProtocolConstants.MAX_PACKET_LENGTH + 1);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addObjectReader(Types.BATCH, batchReader);

		try {
			reader.readStruct(Types.BATCH, UnverifiedBatch.class);
			fail();
		} catch(FormatException expected) {}
		context.assertIsSatisfied();
	}

	@Test
	public void testNoFormatExceptionIfBatchIsMaximumSize() throws Exception {
		final UnverifiedBatchFactory batchFactory =
			context.mock(UnverifiedBatchFactory.class);
		BatchReader batchReader = new BatchReader(crypto, messageReader,
				batchFactory);
		final UnverifiedBatch batch = context.mock(UnverifiedBatch.class);
		context.checking(new Expectations() {{
			oneOf(batchFactory).createUnverifiedBatch(with(any(BatchId.class)),
					with(Collections.singletonList(message)));
			will(returnValue(batch));
		}});

		byte[] b = createBatch(ProtocolConstants.MAX_PACKET_LENGTH);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addObjectReader(Types.BATCH, batchReader);

		assertEquals(batch, reader.readStruct(Types.BATCH,
				UnverifiedBatch.class));
		context.assertIsSatisfied();
	}

	@Test
	public void testBatchId() throws Exception {
		byte[] b = createBatch(ProtocolConstants.MAX_PACKET_LENGTH);
		// Calculate the expected batch ID
		MessageDigest messageDigest = crypto.getMessageDigest();
		messageDigest.update(b);
		final BatchId id = new BatchId(messageDigest.digest());

		final UnverifiedBatchFactory batchFactory =
			context.mock(UnverifiedBatchFactory.class);
		BatchReader batchReader = new BatchReader(crypto, messageReader,
				batchFactory);
		final UnverifiedBatch batch = context.mock(UnverifiedBatch.class);
		context.checking(new Expectations() {{
			// Check that the batch ID matches the expected ID
			oneOf(batchFactory).createUnverifiedBatch(with(id),
					with(Collections.singletonList(message)));
			will(returnValue(batch));
		}});

		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addObjectReader(Types.BATCH, batchReader);

		assertEquals(batch, reader.readStruct(Types.BATCH,
				UnverifiedBatch.class));
		context.assertIsSatisfied();
	}

	@Test
	public void testEmptyBatch() throws Exception {
		final UnverifiedBatchFactory batchFactory =
			context.mock(UnverifiedBatchFactory.class);
		BatchReader batchReader = new BatchReader(crypto, messageReader,
				batchFactory);

		byte[] b = createEmptyBatch();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addObjectReader(Types.BATCH, batchReader);

		try {
			reader.readStruct(Types.BATCH, UnverifiedBatch.class);
			fail();
		} catch(FormatException expected) {}
		context.assertIsSatisfied();
	}

	private byte[] createBatch(int size) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream(size);
		Writer w = writerFactory.createWriter(out);
		w.writeStructId(Types.BATCH);
		w.writeListStart();
		// We're using a fake message reader, so it's OK to use a fake message
		w.writeStructId(Types.MESSAGE);
		w.writeBytes(new byte[size - 10]);
		w.writeListEnd();
		byte[] b = out.toByteArray();
		assertEquals(size, b.length);
		return b;
	}

	private byte[] createEmptyBatch() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructId(Types.BATCH);
		w.writeListStart();
		w.writeListEnd();
		return out.toByteArray();
	}

	private class TestMessageReader implements ObjectReader<UnverifiedMessage> {

		public UnverifiedMessage readObject(Reader r) throws IOException {
			r.readStructId(Types.MESSAGE);
			r.readBytes();
			return message;
		}
	}
}

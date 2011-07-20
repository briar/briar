package net.sf.briar.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

import junit.framework.TestCase;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.Header;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.FormatException;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.serial.SerialModule;

import org.jmock.Mockery;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class BundleReaderImplTest extends TestCase {

	private final Mockery context = new Mockery();
	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;

	public BundleReaderImplTest() {
		Injector i = Guice.createInjector(new SerialModule());
		readerFactory = i.getInstance(ReaderFactory.class);
		writerFactory = i.getInstance(WriterFactory.class);
	}

	@Test
	public void testEmptyBundleThrowsFormatException() throws Exception {
		ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {});
		Reader r = readerFactory.createReader(in);
		BundleReaderImpl b = new BundleReaderImpl(r, new TestHeaderReader(),
				new TestBatchReader());

		try {
			b.getHeader();
			assertTrue(false);
		} catch(FormatException expected) {}
	}

	@Test
	public void testReadingBatchBeforeHeaderThrowsIllegalStateException()
	throws Exception {
		ByteArrayInputStream in = new ByteArrayInputStream(createValidBundle());
		Reader r = readerFactory.createReader(in);
		BundleReaderImpl b = new BundleReaderImpl(r, new TestHeaderReader(),
				new TestBatchReader());

		try {
			b.getNextBatch();
			assertTrue(false);
		} catch(IllegalStateException expected) {}
	}

	@Test
	public void testMissingHeaderThrowsFormatException() throws Exception {
		// Create a headless bundle
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeListStart();
		w.writeUserDefinedTag(Tags.BATCH);
		w.writeList(Collections.emptyList());
		w.writeListEnd();
		w.close();
		byte[] headless = out.toByteArray();
		// Try to read a header from the headless bundle
		ByteArrayInputStream in = new ByteArrayInputStream(headless);
		Reader r = readerFactory.createReader(in);
		BundleReaderImpl b = new BundleReaderImpl(r, new TestHeaderReader(),
				new TestBatchReader());

		try {
			b.getHeader();
			assertTrue(false);
		} catch(FormatException expected) {}
	}

	@Test
	public void testMissingBatchListThrowsFormatException() throws Exception {
		// Create a header-only bundle
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeUserDefinedTag(Tags.HEADER);
		w.writeList(Collections.emptyList()); // Acks
		w.writeList(Collections.emptyList()); // Subs
		w.writeMap(Collections.emptyMap()); // Transports
		w.writeInt64(System.currentTimeMillis()); // Timestamp
		w.close();
		byte[] headerOnly = out.toByteArray();
		// Try to read a header from the header-only bundle
		ByteArrayInputStream in = new ByteArrayInputStream(headerOnly);
		final Reader r = readerFactory.createReader(in);
		BundleReaderImpl b = new BundleReaderImpl(r, new TestHeaderReader(),
				new TestBatchReader());

		try {
			b.getHeader();
			assertTrue(false);
		} catch(FormatException expected) {}
	}

	@Test
	public void testEmptyBatchListIsAcceptable() throws Exception {
		// Create a bundle with no batches
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeUserDefinedTag(Tags.HEADER);
		w.writeList(Collections.emptyList()); // Acks
		w.writeList(Collections.emptyList()); // Subs
		w.writeMap(Collections.emptyMap()); // Transports
		w.writeInt64(System.currentTimeMillis()); // Timestamp
		w.writeListStart();
		w.writeListEnd();
		w.close();
		byte[] batchless = out.toByteArray();
		// It should be possible to read the header and null
		ByteArrayInputStream in = new ByteArrayInputStream(batchless);
		final Reader r = readerFactory.createReader(in);
		BundleReaderImpl b = new BundleReaderImpl(r, new TestHeaderReader(),
				new TestBatchReader());

		assertNotNull(b.getHeader());
		assertNull(b.getNextBatch());
	}

	@Test
	public void testValidBundle() throws Exception {
		// It should be possible to read the header, a batch, and null
		ByteArrayInputStream in = new ByteArrayInputStream(createValidBundle());
		final Reader r = readerFactory.createReader(in);
		BundleReaderImpl b = new BundleReaderImpl(r, new TestHeaderReader(),
				new TestBatchReader());

		assertNotNull(b.getHeader());
		assertNotNull(b.getNextBatch());
		assertNull(b.getNextBatch());
	}

	@Test
	public void testReadingBatchAfterNullThrowsIllegalStateException()
	throws Exception {
		// Trying to read another batch after null should not succeed
		ByteArrayInputStream in = new ByteArrayInputStream(createValidBundle());
		final Reader r = readerFactory.createReader(in);
		BundleReaderImpl b = new BundleReaderImpl(r, new TestHeaderReader(),
				new TestBatchReader());

		assertNotNull(b.getHeader());
		assertNotNull(b.getNextBatch());
		assertNull(b.getNextBatch());
		try {
			b.getNextBatch();
			assertTrue(false);
		} catch(IllegalStateException expected) {}
	}

	@Test
	public void testReadingHeaderTwiceThrowsIllegalStateException()
	throws Exception {
		// Trying to read the header twice should not succeed
		ByteArrayInputStream in = new ByteArrayInputStream(createValidBundle());
		final Reader r = readerFactory.createReader(in);
		BundleReaderImpl b = new BundleReaderImpl(r, new TestHeaderReader(),
				new TestBatchReader());

		assertNotNull(b.getHeader());
		try {
			b.getHeader();
			assertTrue(false);
		} catch(IllegalStateException expected) {}
	}

	@Test
	public void testReadingHeaderAfterBatchThrowsIllegalStateException()
	throws Exception {
		// Trying to read the header after a batch should not succeed
		ByteArrayInputStream in = new ByteArrayInputStream(createValidBundle());
		final Reader r = readerFactory.createReader(in);
		BundleReaderImpl b = new BundleReaderImpl(r, new TestHeaderReader(),
				new TestBatchReader());

		assertNotNull(b.getHeader());
		assertNotNull(b.getNextBatch());
		try {
			b.getHeader();
			assertTrue(false);
		} catch(IllegalStateException expected) {}
	}

	private byte[] createValidBundle() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeUserDefinedTag(Tags.HEADER);
		w.writeList(Collections.emptyList()); // Acks
		w.writeList(Collections.emptyList()); // Subs
		w.writeMap(Collections.emptyMap()); // Transports
		w.writeInt64(System.currentTimeMillis()); // Timestamp
		w.writeListStart();
		w.writeUserDefinedTag(Tags.BATCH);
		w.writeList(Collections.emptyList()); // Messages
		w.writeListEnd();
		w.close();
		return out.toByteArray();
	}

	private class TestHeaderReader implements ObjectReader<Header> {

		public Header readObject(Reader r) throws IOException,
		GeneralSecurityException {
			r.readList();
			r.readList();
			r.readMap();
			r.readInt64();
			return context.mock(Header.class);
		}
	}

	private class TestBatchReader implements ObjectReader<Batch> {

		public Batch readObject(Reader r) throws IOException,
		GeneralSecurityException {
			r.readList();
			return context.mock(Batch.class);
		}
	}
}

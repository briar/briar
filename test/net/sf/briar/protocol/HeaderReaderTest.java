package net.sf.briar.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Random;

import junit.framework.TestCase;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.FormatException;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.serial.SerialModule;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class HeaderReaderTest extends TestCase {

	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;
	private final Mockery context;

	public HeaderReaderTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new SerialModule());
		readerFactory = i.getInstance(ReaderFactory.class);
		writerFactory = i.getInstance(WriterFactory.class);
		context = new Mockery();
	}

	@Test
	public void testFormatExceptionIfHeaderIsTooLarge() throws Exception {
		HeaderFactory headerFactory = context.mock(HeaderFactory.class);
		HeaderReader headerReader = new HeaderReader(headerFactory);

		byte[] b = createHeader(Header.MAX_SIZE + 1);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addObjectReader(Tags.HEADER, headerReader);

		reader.readUserDefinedTag(Tags.HEADER);
		try {
			reader.readUserDefinedObject(Tags.HEADER, Header.class);
			assertTrue(false);
		} catch(FormatException expected) {}
		context.assertIsSatisfied();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testNoFormatExceptionIfHeaderIsMaximumSize() throws Exception {
		final HeaderFactory headerFactory = context.mock(HeaderFactory.class);
		HeaderReader headerReader = new HeaderReader(headerFactory);
		final Header header = context.mock(Header.class);
		context.checking(new Expectations() {{
			oneOf(headerFactory).createHeader(
					with(Collections.<BatchId>emptyList()),
					with(any(Collection.class)), with(any(Map.class)),
					with(any(long.class)));
			will(returnValue(header));
		}});

		byte[] b = createHeader(Header.MAX_SIZE);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addObjectReader(Tags.HEADER, headerReader);

		reader.readUserDefinedTag(Tags.HEADER);
		assertEquals(header, reader.readUserDefinedObject(Tags.HEADER,
				Header.class));
		context.assertIsSatisfied();
	}

	@Test
	public void testEmptyHeader() throws Exception {
		final HeaderFactory headerFactory = context.mock(HeaderFactory.class);
		HeaderReader headerReader = new HeaderReader(headerFactory);
		final Header header = context.mock(Header.class);
		context.checking(new Expectations() {{
			oneOf(headerFactory).createHeader(
					with(Collections.<BatchId>emptyList()),
					with(Collections.<GroupId>emptyList()),
					with(Collections.<String, String>emptyMap()),
					with(any(long.class)));
			will(returnValue(header));
		}});

		byte[] b = createEmptyHeader();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addObjectReader(Tags.HEADER, headerReader);

		reader.readUserDefinedTag(Tags.HEADER);
		assertEquals(header, reader.readUserDefinedObject(Tags.HEADER,
				Header.class));
		context.assertIsSatisfied();
	}

	private byte[] createHeader(int size) throws Exception {
		Random random = new Random();
		ByteArrayOutputStream out = new ByteArrayOutputStream(size);
		Writer w = writerFactory.createWriter(out);
		w.writeUserDefinedTag(Tags.HEADER);
		// Acks
		w.writeListStart();
		w.writeListEnd();
		// Subs
		w.writeListStart();
		// Fill most of the header with subscriptions
		while(w.getBytesWritten() < size - 45) {
			w.writeUserDefinedTag(Tags.GROUP_ID);
			byte[] b = new byte[UniqueId.LENGTH];
			random.nextBytes(b);
			w.writeRaw(b);
		}
		w.writeListEnd();
		// Transports
		w.writeMapStart();
		w.writeString("foo");
		// Build a string that will bring the header up to the expected size
		int length = (int) (size - w.getBytesWritten() - 12);
		StringBuilder s = new StringBuilder();
		for(int i = 0; i < length; i++) s.append((char) ('0' + i % 10));
		w.writeString(s.toString());
		w.writeMapEnd();
		// Timestamp
		w.writeInt64(System.currentTimeMillis());
		w.close();
		byte[] b = out.toByteArray();
		assertEquals(size, b.length);
		return b;
	}

	private byte[] createEmptyHeader() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeUserDefinedTag(Tags.HEADER);
		// Acks
		w.writeListStart();
		w.writeListEnd();
		// Subs
		w.writeListStart();
		w.writeListEnd();
		// Transports
		w.writeMapStart();
		w.writeMapEnd();
		// Timestamp
		w.writeInt64(System.currentTimeMillis());
		w.close();
		return out.toByteArray();
	}
}

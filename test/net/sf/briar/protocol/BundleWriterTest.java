package net.sf.briar.protocol;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.BundleWriter;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.serial.Writer;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public class BundleWriterTest extends TestCase {

	private final long capacity = 1024L * 1024L;
	private final BatchId ack = new BatchId(TestUtils.getRandomId());
	private final Set<BatchId> acks = Collections.singleton(ack);
	private final GroupId sub = new GroupId(TestUtils.getRandomId());
	private final Set<GroupId> subs = Collections.singleton(sub);
	private final Map<String, String> transports =
		Collections.singletonMap("foo", "bar");
	private final byte[] headerSig = TestUtils.getRandomId();
	private final byte[] messageBody = new byte[123];
	private final byte[] batchSig = TestUtils.getRandomId();

	@Test
	public void testAddHeader() throws IOException {
		Mockery context = new Mockery();
		final Writer writer = context.mock(Writer.class);
		final Header header = context.mock(Header.class);
		context.checking(new Expectations() {{
			// Acks
			oneOf(writer).writeListStart();
			oneOf(header).getAcks();
			will(returnValue(acks));
			oneOf(writer).writeRaw(ack);
			oneOf(writer).writeListEnd();
			// Subs
			oneOf(writer).writeListStart();
			oneOf(header).getSubscriptions();
			will(returnValue(subs));
			oneOf(writer).writeRaw(sub);
			oneOf(writer).writeListEnd();
			// Transports
			oneOf(header).getTransports();
			will(returnValue(transports));
			oneOf(writer).writeMap(transports);
			// Signature
			oneOf(header).getSignature();
			will(returnValue(headerSig));
			oneOf(writer).writeRaw(headerSig);
		}});
		BundleWriter w = new BundleWriterImpl(writer, capacity);

		w.addHeader(header);

		context.assertIsSatisfied();
	}

	@Test
	public void testAddHeaderEmptyLists() throws IOException {
		Mockery context = new Mockery();
		final Writer writer = context.mock(Writer.class);
		final Header header = context.mock(Header.class);
		context.checking(new Expectations() {{
			// Acks
			oneOf(writer).writeListStart();
			oneOf(header).getAcks();
			will(returnValue(Collections.emptySet()));
			oneOf(writer).writeListEnd();
			// Subs
			oneOf(writer).writeListStart();
			oneOf(header).getSubscriptions();
			will(returnValue(Collections.emptySet()));
			oneOf(writer).writeListEnd();
			// Transports
			oneOf(header).getTransports();
			will(returnValue(Collections.emptyMap()));
			oneOf(writer).writeMap(Collections.emptyMap());
			// Signature
			oneOf(header).getSignature();
			will(returnValue(headerSig));
			oneOf(writer).writeRaw(headerSig);
		}});
		BundleWriter w = new BundleWriterImpl(writer, capacity);

		w.addHeader(header);

		context.assertIsSatisfied();
	}

	@Test
	public void testBatchBeforeHeaderThrowsException() throws IOException {
		Mockery context = new Mockery();
		final Writer writer = context.mock(Writer.class);
		final Batch batch = context.mock(Batch.class);
		BundleWriter w = new BundleWriterImpl(writer, capacity);

		try {
			w.addBatch(batch);
			assertTrue(false);
		} catch(IllegalStateException expected) {}

		context.assertIsSatisfied();
	}

	@Test
	public void testCloseBeforeHeaderThrowsException() throws IOException {
		Mockery context = new Mockery();
		final Writer writer = context.mock(Writer.class);
		BundleWriter w = new BundleWriterImpl(writer, capacity);

		try {
			w.close();
			assertTrue(false);
		} catch(IllegalStateException expected) {}

		context.assertIsSatisfied();
	}

	@Test
	public void testCloseWithoutBatchesDoesNotThrowException()
	throws IOException {
		Mockery context = new Mockery();
		final Writer writer = context.mock(Writer.class);
		final Header header = context.mock(Header.class);
		context.checking(new Expectations() {{
			// Acks
			oneOf(writer).writeListStart();
			oneOf(header).getAcks();
			will(returnValue(acks));
			oneOf(writer).writeRaw(ack);
			oneOf(writer).writeListEnd();
			// Subs
			oneOf(writer).writeListStart();
			oneOf(header).getSubscriptions();
			will(returnValue(subs));
			oneOf(writer).writeRaw(sub);
			oneOf(writer).writeListEnd();
			// Transports
			oneOf(header).getTransports();
			will(returnValue(transports));
			oneOf(writer).writeMap(transports);
			// Signature
			oneOf(header).getSignature();
			will(returnValue(headerSig));
			oneOf(writer).writeRaw(headerSig);
			// Close - write an empty list of batches
			oneOf(writer).writeListStart();
			oneOf(writer).writeListEnd();
			oneOf(writer).close();
		}});
		BundleWriter w = new BundleWriterImpl(writer, capacity);

		w.addHeader(header);
		w.close();

		context.assertIsSatisfied();
	}

	@Test
	public void testAddHeaderAndTwoBatches() throws IOException {
		Mockery context = new Mockery();
		final Writer writer = context.mock(Writer.class);
		final Header header = context.mock(Header.class);
		final Batch batch = context.mock(Batch.class);
		final Message message = context.mock(Message.class);
		context.checking(new Expectations() {{
			// Acks
			oneOf(writer).writeListStart();
			oneOf(header).getAcks();
			will(returnValue(acks));
			oneOf(writer).writeRaw(ack);
			oneOf(writer).writeListEnd();
			// Subs
			oneOf(writer).writeListStart();
			oneOf(header).getSubscriptions();
			will(returnValue(subs));
			oneOf(writer).writeRaw(sub);
			oneOf(writer).writeListEnd();
			// Transports
			oneOf(header).getTransports();
			will(returnValue(transports));
			oneOf(writer).writeMap(transports);
			// Signature
			oneOf(header).getSignature();
			will(returnValue(headerSig));
			oneOf(writer).writeRaw(headerSig);
			// First batch
			oneOf(writer).writeListStart();
			oneOf(writer).writeListStart();
			oneOf(batch).getMessages();
			will(returnValue(Collections.singleton(message)));
			oneOf(message).getBytes();
			will(returnValue(messageBody));
			oneOf(writer).writeRaw(messageBody);
			oneOf(writer).writeListEnd();
			oneOf(batch).getSignature();
			will(returnValue(batchSig));
			oneOf(writer).writeRaw(batchSig);
			// Second batch
			oneOf(writer).writeListStart();
			oneOf(batch).getMessages();
			will(returnValue(Collections.singleton(message)));
			oneOf(message).getBytes();
			will(returnValue(messageBody));
			oneOf(writer).writeRaw(messageBody);
			oneOf(writer).writeListEnd();
			oneOf(batch).getSignature();
			will(returnValue(batchSig));
			oneOf(writer).writeRaw(batchSig);
			// Close
			oneOf(writer).writeListEnd();
			oneOf(writer).close();
		}});
		BundleWriter w = new BundleWriterImpl(writer, capacity);

		w.addHeader(header);
		w.addBatch(batch);
		w.addBatch(batch);
		w.close();

		context.assertIsSatisfied();
	}
}

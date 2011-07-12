package net.sf.briar.protocol;

import java.io.IOException;
import java.security.SignatureException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchBuilder;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.Header;
import net.sf.briar.api.protocol.HeaderBuilder;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageParser;
import net.sf.briar.api.serial.Raw;
import net.sf.briar.api.serial.Reader;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import com.google.inject.Provider;

public class BundleReaderTest extends TestCase {

	private final long size = 1024L * 1024L;
	private final BatchId ack = new BatchId(TestUtils.getRandomId());
	private final List<Raw> rawAcks =
		Collections.<Raw>singletonList(new TestRaw(ack.getBytes()));
	private final Set<BatchId> acks = Collections.singleton(ack);
	private final GroupId sub = new GroupId(TestUtils.getRandomId());
	private final List<Raw> rawSubs =
		Collections.<Raw>singletonList(new TestRaw(sub.getBytes()));
	private final Set<GroupId> subs = Collections.singleton(sub);
	private final Map<String, String> transports =
		Collections.singletonMap("foo", "bar");
	private final byte[] headerSig = TestUtils.getRandomId();
	private final byte[] messageBody = new byte[123];
	private final List<Raw> rawMessages =
		Collections.<Raw>singletonList(new TestRaw(messageBody));
	private final byte[] batchSig = TestUtils.getRandomId();

	@Test
	public void testGetHeader() throws IOException, SignatureException {
		Mockery context = new Mockery();
		final Reader reader = context.mock(Reader.class);
		final MessageParser messageParser = context.mock(MessageParser.class);
		@SuppressWarnings("unchecked")
		final Provider<HeaderBuilder> headerBuilderProvider =
			context.mock(Provider.class);
		@SuppressWarnings("unchecked")
		final Provider<BatchBuilder> batchBuilderProvider =
			context.mock(Provider.class, "batchBuilderProvider");
		final HeaderBuilder headerBuilder = context.mock(HeaderBuilder.class);
		final Header header = context.mock(Header.class);
		context.checking(new Expectations() {{
			oneOf(reader).setReadLimit(Header.MAX_SIZE);
			oneOf(headerBuilderProvider).get();
			will(returnValue(headerBuilder));
			// Acks
			oneOf(reader).readList(Raw.class);
			will(returnValue(rawAcks));
			oneOf(headerBuilder).addAcks(acks);
			// Subs
			oneOf(reader).readList(Raw.class);
			will(returnValue(rawSubs));
			oneOf(headerBuilder).addSubscriptions(subs);
			// Transports
			oneOf(reader).readMap(String.class, String.class);
			will(returnValue(transports));
			oneOf(headerBuilder).addTransports(transports);
			// Signature
			oneOf(reader).readRaw();
			will(returnValue(headerSig));
			oneOf(headerBuilder).setSignature(headerSig);
			// Build the header
			oneOf(headerBuilder).build();
			will(returnValue(header));
		}});
		BundleReader r = createBundleReader(reader, messageParser,
				headerBuilderProvider, batchBuilderProvider);

		assertEquals(header, r.getHeader());

		context.assertIsSatisfied();
	}

	@Test
	public void testBatchBeforeHeaderThrowsException() throws IOException,
	SignatureException {
		Mockery context = new Mockery();
		final Reader reader = context.mock(Reader.class);
		final MessageParser messageParser = context.mock(MessageParser.class);
		@SuppressWarnings("unchecked")
		final Provider<HeaderBuilder> headerBuilderProvider =
			context.mock(Provider.class);
		@SuppressWarnings("unchecked")
		final Provider<BatchBuilder> batchBuilderProvider =
			context.mock(Provider.class, "batchBuilderProvider");
		BundleReader r = createBundleReader(reader, messageParser,
				headerBuilderProvider, batchBuilderProvider);

		try {
			r.getNextBatch();
			assertTrue(false);
		} catch(IllegalStateException expected) {}

		context.assertIsSatisfied();
	}

	@Test
	public void testGetHeaderNoBatches() throws IOException,
	SignatureException {
		Mockery context = new Mockery();
		final Reader reader = context.mock(Reader.class);
		final MessageParser messageParser = context.mock(MessageParser.class);
		@SuppressWarnings("unchecked")
		final Provider<HeaderBuilder> headerBuilderProvider =
			context.mock(Provider.class);
		@SuppressWarnings("unchecked")
		final Provider<BatchBuilder> batchBuilderProvider =
			context.mock(Provider.class, "batchBuilderProvider");
		final HeaderBuilder headerBuilder = context.mock(HeaderBuilder.class);
		final Header header = context.mock(Header.class);
		context.checking(new Expectations() {{
			oneOf(reader).setReadLimit(Header.MAX_SIZE);
			oneOf(headerBuilderProvider).get();
			will(returnValue(headerBuilder));
			// Acks
			oneOf(reader).readList(Raw.class);
			will(returnValue(rawAcks));
			oneOf(headerBuilder).addAcks(acks);
			// Subs
			oneOf(reader).readList(Raw.class);
			will(returnValue(rawSubs));
			oneOf(headerBuilder).addSubscriptions(subs);
			// Transports
			oneOf(reader).readMap(String.class, String.class);
			will(returnValue(transports));
			oneOf(headerBuilder).addTransports(transports);
			// Signature
			oneOf(reader).readRaw();
			will(returnValue(headerSig));
			oneOf(headerBuilder).setSignature(headerSig);
			// Build the header
			oneOf(headerBuilder).build();
			will(returnValue(header));
			// No batches
			oneOf(reader).readListStart();
			oneOf(reader).hasListEnd();
			will(returnValue(true));
			oneOf(reader).readListEnd();
		}});
		BundleReader r = createBundleReader(reader, messageParser,
				headerBuilderProvider, batchBuilderProvider);

		assertEquals(header, r.getHeader());
		assertNull(r.getNextBatch());

		context.assertIsSatisfied();
	}

	@Test
	public void testGetHeaderOneBatch() throws IOException,
	SignatureException {
		Mockery context = new Mockery();
		final Reader reader = context.mock(Reader.class);
		final MessageParser messageParser = context.mock(MessageParser.class);
		@SuppressWarnings("unchecked")
		final Provider<HeaderBuilder> headerBuilderProvider =
			context.mock(Provider.class);
		@SuppressWarnings("unchecked")
		final Provider<BatchBuilder> batchBuilderProvider =
			context.mock(Provider.class, "batchBuilderProvider");
		final HeaderBuilder headerBuilder = context.mock(HeaderBuilder.class);
		final Header header = context.mock(Header.class);
		final BatchBuilder batchBuilder = context.mock(BatchBuilder.class);
		final Batch batch = context.mock(Batch.class);
		final Message message = context.mock(Message.class);
		context.checking(new Expectations() {{
			oneOf(reader).setReadLimit(Header.MAX_SIZE);
			oneOf(headerBuilderProvider).get();
			will(returnValue(headerBuilder));
			// Acks
			oneOf(reader).readList(Raw.class);
			will(returnValue(rawAcks));
			oneOf(headerBuilder).addAcks(acks);
			// Subs
			oneOf(reader).readList(Raw.class);
			will(returnValue(rawSubs));
			oneOf(headerBuilder).addSubscriptions(subs);
			// Transports
			oneOf(reader).readMap(String.class, String.class);
			will(returnValue(transports));
			oneOf(headerBuilder).addTransports(transports);
			// Signature
			oneOf(reader).readRaw();
			will(returnValue(headerSig));
			oneOf(headerBuilder).setSignature(headerSig);
			// Build the header
			oneOf(headerBuilder).build();
			will(returnValue(header));
			// First batch
			oneOf(reader).readListStart();
			oneOf(reader).hasListEnd();
			will(returnValue(false));
			oneOf(reader).setReadLimit(Batch.MAX_SIZE);
			oneOf(batchBuilderProvider).get();
			will(returnValue(batchBuilder));
			oneOf(reader).readList(Raw.class);
			will(returnValue(rawMessages));
			oneOf(messageParser).parseMessage(messageBody);
			will(returnValue(message));
			oneOf(batchBuilder).addMessage(message);
			oneOf(reader).readRaw();
			will(returnValue(batchSig));
			oneOf(batchBuilder).setSignature(batchSig);
			oneOf(batchBuilder).build();
			will(returnValue(batch));
			// No more batches
			oneOf(reader).hasListEnd();
			will(returnValue(true));
			oneOf(reader).readListEnd();
		}});
		BundleReader r = createBundleReader(reader, messageParser,
				headerBuilderProvider, batchBuilderProvider);

		assertEquals(header, r.getHeader());
		assertEquals(batch, r.getNextBatch());
		assertNull(r.getNextBatch());

		context.assertIsSatisfied();
	}

	private BundleReader createBundleReader(Reader reader,
			MessageParser messageParser,
			Provider<HeaderBuilder> headerBuilderProvider,
			Provider<BatchBuilder> batchBuilderProvider) {
		return new BundleReader(reader, messageParser, headerBuilderProvider,
				batchBuilderProvider) {
			public long getSize() {
				return size;
			}
		};
	}
}

package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.record.Record;
import org.briarproject.bramble.api.record.RecordReader;
import org.briarproject.bramble.api.record.RecordReader.RecordPredicate;
import org.briarproject.bramble.api.sync.Ack;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.Offer;
import org.briarproject.bramble.api.sync.Priority;
import org.briarproject.bramble.api.sync.Request;
import org.briarproject.bramble.api.sync.SyncRecordReader;
import org.briarproject.bramble.api.sync.Versions;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.PredicateMatcher;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.briarproject.bramble.api.record.Record.MAX_RECORD_PAYLOAD_BYTES;
import static org.briarproject.bramble.api.sync.RecordTypes.ACK;
import static org.briarproject.bramble.api.sync.RecordTypes.MESSAGE;
import static org.briarproject.bramble.api.sync.RecordTypes.OFFER;
import static org.briarproject.bramble.api.sync.RecordTypes.PRIORITY;
import static org.briarproject.bramble.api.sync.RecordTypes.REQUEST;
import static org.briarproject.bramble.api.sync.RecordTypes.VERSIONS;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_IDS;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_SUPPORTED_VERSIONS;
import static org.briarproject.bramble.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.PRIORITY_NONCE_BYTES;
import static org.briarproject.bramble.api.sync.SyncConstants.PROTOCOL_VERSION;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SyncRecordReaderImplTest extends BrambleMockTestCase {

	private final MessageFactory messageFactory =
			context.mock(MessageFactory.class);
	private final RecordReader recordReader = context.mock(RecordReader.class);

	private final SyncRecordReader reader =
			new SyncRecordReaderImpl(messageFactory, recordReader);

	@Test
	public void testNoFormatExceptionIfMessageIsMinimumSize() throws Exception {
		expectReadRecord(createMessage(MESSAGE_HEADER_LENGTH + 1));
		expectCreateMessage(1);

		reader.readMessage();
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfMessageIsTooSmall() throws Exception {
		expectReadRecord(createMessage(MESSAGE_HEADER_LENGTH));

		reader.readMessage();
	}

	@Test
	public void testNoFormatExceptionIfMessageIsMaximumSize() throws Exception {
		expectReadRecord(createMessage(MESSAGE_HEADER_LENGTH
				+ MAX_MESSAGE_BODY_LENGTH));
		expectCreateMessage(MAX_MESSAGE_BODY_LENGTH);

		reader.readMessage();
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfMessageIsTooLarge() throws Exception {
		expectReadRecord(createMessage(MESSAGE_HEADER_LENGTH
				+ MAX_MESSAGE_BODY_LENGTH + 1));

		reader.readMessage();
	}

	@Test
	public void testNoFormatExceptionIfAckIsMaximumSize() throws Exception {
		expectReadRecord(createAck());

		Ack ack = reader.readAck();
		assertEquals(MAX_MESSAGE_IDS, ack.getMessageIds().size());
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfAckIsEmpty() throws Exception {
		expectReadRecord(createEmptyAck());

		reader.readAck();
	}

	@Test
	public void testNoFormatExceptionIfOfferIsMaximumSize() throws Exception {
		expectReadRecord(createOffer());

		Offer offer = reader.readOffer();
		assertEquals(MAX_MESSAGE_IDS, offer.getMessageIds().size());
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfOfferIsEmpty() throws Exception {
		expectReadRecord(createEmptyOffer());

		reader.readOffer();
	}

	@Test
	public void testNoFormatExceptionIfRequestIsMaximumSize() throws Exception {
		expectReadRecord(createRequest());

		Request request = reader.readRequest();
		assertEquals(MAX_MESSAGE_IDS, request.getMessageIds().size());
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfRequestIsEmpty() throws Exception {
		expectReadRecord(createEmptyRequest());

		reader.readRequest();
	}

	@Test
	public void testNoFormatExceptionIfVersionsIsMaximumSize()
			throws Exception {
		expectReadRecord(createVersions(MAX_SUPPORTED_VERSIONS));

		Versions versions = reader.readVersions();
		List<Byte> supported = versions.getSupportedVersions();
		assertEquals(MAX_SUPPORTED_VERSIONS, supported.size());
		for (int i = 0; i < supported.size(); i++) {
			assertEquals(i, (int) supported.get(i));
		}
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfVersionsIsEmpty() throws Exception {
		expectReadRecord(createVersions(0));

		reader.readVersions();
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfVersionsIsTooLarge() throws Exception {
		expectReadRecord(createVersions(MAX_SUPPORTED_VERSIONS + 1));

		reader.readVersions();
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfPriorityNonceIsTooSmall()
			throws Exception {
		expectReadRecord(createPriority(PRIORITY_NONCE_BYTES - 1));

		reader.readPriority();
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfPriorityNonceIsTooLarge()
			throws Exception {
		expectReadRecord(createPriority(PRIORITY_NONCE_BYTES + 1));

		reader.readPriority();
	}

	@Test
	public void testNoFormatExceptionIfPriorityNonceIsCorrectSize()
			throws Exception {
		expectReadRecord(createPriority(PRIORITY_NONCE_BYTES));

		Priority priority = reader.readPriority();
		assertEquals(PRIORITY_NONCE_BYTES, priority.getNonce().length);
	}

	@Test
	public void testEofReturnsTrueWhenAtEndOfStream() throws Exception {
		expectReadRecord(createAck());
		expectReadEof();

		SyncRecordReader reader =
				new SyncRecordReaderImpl(messageFactory, recordReader);
		assertFalse(reader.eof());
		assertTrue(reader.hasAck());
		Ack ack = reader.readAck();
		assertEquals(MAX_MESSAGE_IDS, ack.getMessageIds().size());
		assertTrue(reader.eof());
		assertTrue(reader.eof());
	}

	private void expectCreateMessage(int bodyLength) {
		MessageId messageId = new MessageId(getRandomId());
		GroupId groupId = new GroupId(getRandomId());
		long timestamp = System.currentTimeMillis();

		context.checking(new Expectations() {{
			Matcher<byte[]> matcher = new PredicateMatcher<>(byte[].class,
					b -> b.length == MESSAGE_HEADER_LENGTH + bodyLength);
			oneOf(messageFactory).createMessage(with(matcher));
			will(returnValue(new Message(messageId, groupId, timestamp,
					new byte[bodyLength])));
		}});
	}

	private void expectReadRecord(Record record) throws Exception {
		context.checking(new Expectations() {{
			// Test that the `accept` predicate passed to the reader would
			// accept the expected record
			oneOf(recordReader).readRecord(with(new PredicateMatcher<>(
							RecordPredicate.class, rp -> rp.test(record))),
					with(any(RecordPredicate.class)));
			will(returnValue(record));
		}});
	}

	private void expectReadEof() throws Exception {
		context.checking(new Expectations() {{
			oneOf(recordReader).readRecord(with(any(RecordPredicate.class)),
					with(any(RecordPredicate.class)));
			will(returnValue(null));
		}});
	}

	private Record createMessage(int payloadLength) {
		return new Record(PROTOCOL_VERSION, MESSAGE, new byte[payloadLength]);
	}

	private Record createAck() throws Exception {
		return new Record(PROTOCOL_VERSION, ACK, createPayload());
	}

	private Record createEmptyAck() {
		return new Record(PROTOCOL_VERSION, ACK, new byte[0]);
	}

	private Record createOffer() throws Exception {
		return new Record(PROTOCOL_VERSION, OFFER, createPayload());
	}

	private Record createEmptyOffer() {
		return new Record(PROTOCOL_VERSION, OFFER, new byte[0]);
	}

	private Record createRequest() throws Exception {
		return new Record(PROTOCOL_VERSION, REQUEST, createPayload());
	}

	private Record createEmptyRequest() {
		return new Record(PROTOCOL_VERSION, REQUEST, new byte[0]);
	}

	private Record createVersions(int numVersions) {
		byte[] payload = new byte[numVersions];
		for (int i = 0; i < payload.length; i++) payload[i] = (byte) i;
		return new Record(PROTOCOL_VERSION, VERSIONS, payload);
	}

	private Record createPriority(int nonceBytes) {
		byte[] payload = getRandomBytes(nonceBytes);
		return new Record(PROTOCOL_VERSION, PRIORITY, payload);
	}

	private byte[] createPayload() throws Exception {
		ByteArrayOutputStream payload = new ByteArrayOutputStream();
		while (payload.size() + UniqueId.LENGTH <= MAX_RECORD_PAYLOAD_BYTES) {
			payload.write(getRandomId());
		}
		return payload.toByteArray();
	}
}

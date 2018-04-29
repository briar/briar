package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.record.Record;
import org.briarproject.bramble.api.record.RecordReader;
import org.briarproject.bramble.api.sync.Ack;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.Offer;
import org.briarproject.bramble.api.sync.Request;
import org.briarproject.bramble.api.sync.SyncRecordReader;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;

import static org.briarproject.bramble.api.record.Record.MAX_RECORD_PAYLOAD_BYTES;
import static org.briarproject.bramble.api.sync.RecordTypes.ACK;
import static org.briarproject.bramble.api.sync.RecordTypes.OFFER;
import static org.briarproject.bramble.api.sync.RecordTypes.REQUEST;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_IDS;
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

	@Test
	public void testNoFormatExceptionIfAckIsMaximumSize() throws Exception {
		expectReadRecord(createAck());

		SyncRecordReader reader =
				new SyncRecordReaderImpl(messageFactory, recordReader);
		Ack ack = reader.readAck();
		assertEquals(MAX_MESSAGE_IDS, ack.getMessageIds().size());
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfAckIsEmpty() throws Exception {
		expectReadRecord(createEmptyAck());

		SyncRecordReader reader =
				new SyncRecordReaderImpl(messageFactory, recordReader);
		reader.readAck();
	}

	@Test
	public void testNoFormatExceptionIfOfferIsMaximumSize() throws Exception {
		expectReadRecord(createOffer());

		SyncRecordReader reader =
				new SyncRecordReaderImpl(messageFactory, recordReader);
		Offer offer = reader.readOffer();
		assertEquals(MAX_MESSAGE_IDS, offer.getMessageIds().size());
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfOfferIsEmpty() throws Exception {
		expectReadRecord(createEmptyOffer());

		SyncRecordReader reader =
				new SyncRecordReaderImpl(messageFactory, recordReader);
		reader.readOffer();
	}

	@Test
	public void testNoFormatExceptionIfRequestIsMaximumSize() throws Exception {
		expectReadRecord(createRequest());

		SyncRecordReader reader =
				new SyncRecordReaderImpl(messageFactory, recordReader);
		Request request = reader.readRequest();
		assertEquals(MAX_MESSAGE_IDS, request.getMessageIds().size());
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfRequestIsEmpty() throws Exception {
		expectReadRecord(createEmptyRequest());

		SyncRecordReader reader =
				new SyncRecordReaderImpl(messageFactory, recordReader);
		reader.readRequest();
	}

	@Test
	public void testEofReturnsTrueWhenAtEndOfStream() throws Exception {
		context.checking(new Expectations() {{
			oneOf(recordReader).readRecord();
			will(throwException(new EOFException()));
		}});

		SyncRecordReader reader =
				new SyncRecordReaderImpl(messageFactory, recordReader);
		assertTrue(reader.eof());
		assertTrue(reader.eof());
	}

	@Test
	public void testEofReturnsFalseWhenNotAtEndOfStream() throws Exception {
		expectReadRecord(createAck());

		SyncRecordReader reader =
				new SyncRecordReaderImpl(messageFactory, recordReader);
		assertFalse(reader.eof());
		assertFalse(reader.eof());
	}

	@Test(expected = FormatException.class)
	public void testThrowsExceptionIfProtocolVersionIsUnrecognised()
			throws Exception {
		byte version = (byte) (PROTOCOL_VERSION + 1);
		byte[] payload = getRandomId();

		expectReadRecord(new Record(version, ACK, payload));

		SyncRecordReader reader =
				new SyncRecordReaderImpl(messageFactory, recordReader);
		reader.eof();
	}

	@Test
	public void testSkipsUnrecognisedRecordTypes() throws Exception {
		byte type1 = (byte) (REQUEST + 1);
		byte[] payload1 = getRandomBytes(123);
		Record unknownRecord1 = new Record(PROTOCOL_VERSION, type1, payload1);
		byte type2 = (byte) (REQUEST + 2);
		byte[] payload2 = new byte[0];
		Record unknownRecord2 = new Record(PROTOCOL_VERSION, type2, payload2);
		Record ackRecord = createAck();

		context.checking(new Expectations() {{
			oneOf(recordReader).readRecord();
			will(returnValue(unknownRecord1));
			oneOf(recordReader).readRecord();
			will(returnValue(unknownRecord2));
			oneOf(recordReader).readRecord();
			will(returnValue(ackRecord));

		}});

		SyncRecordReader reader =
				new SyncRecordReaderImpl(messageFactory, recordReader);
		assertTrue(reader.hasAck());
		Ack a = reader.readAck();
		assertEquals(MAX_MESSAGE_IDS, a.getMessageIds().size());
	}

	private void expectReadRecord(Record record) throws Exception {
		context.checking(new Expectations() {{
			oneOf(recordReader).readRecord();
			will(returnValue(record));
		}});
	}

	private Record createAck() throws Exception {
		return new Record(PROTOCOL_VERSION, ACK, createPayload());
	}

	private Record createEmptyAck() throws Exception {
		return new Record(PROTOCOL_VERSION, ACK, new byte[0]);
	}

	private Record createOffer() throws Exception {
		return new Record(PROTOCOL_VERSION, OFFER, createPayload());
	}

	private Record createEmptyOffer() throws Exception {
		return new Record(PROTOCOL_VERSION, OFFER, new byte[0]);
	}

	private Record createRequest() throws Exception {
		return new Record(PROTOCOL_VERSION, REQUEST, createPayload());
	}

	private Record createEmptyRequest() throws Exception {
		return new Record(PROTOCOL_VERSION, REQUEST, new byte[0]);
	}

	private byte[] createPayload() throws Exception {
		ByteArrayOutputStream payload = new ByteArrayOutputStream();
		while (payload.size() + UniqueId.LENGTH <= MAX_RECORD_PAYLOAD_BYTES) {
			payload.write(getRandomId());
		}
		return payload.toByteArray();
	}
}

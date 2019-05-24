package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.Predicate;
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

import javax.annotation.Nullable;

import static org.briarproject.bramble.api.record.Record.MAX_RECORD_PAYLOAD_BYTES;
import static org.briarproject.bramble.api.sync.RecordTypes.ACK;
import static org.briarproject.bramble.api.sync.RecordTypes.OFFER;
import static org.briarproject.bramble.api.sync.RecordTypes.REQUEST;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_IDS;
import static org.briarproject.bramble.api.sync.SyncConstants.PROTOCOL_VERSION;
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
		expectReadRecord(createAck());
		expectReadRecord(null);

		SyncRecordReader reader =
				new SyncRecordReaderImpl(messageFactory, recordReader);
		assertFalse(reader.eof());
		assertTrue(reader.hasAck());
		Ack ack = reader.readAck();
		assertEquals(MAX_MESSAGE_IDS, ack.getMessageIds().size());
		assertTrue(reader.eof());
		assertTrue(reader.eof());
	}

	private void expectReadRecord(@Nullable Record record) throws Exception {
		context.checking(new Expectations() {{
			//noinspection unchecked
			oneOf(recordReader).readRecord(with(any(Predicate.class)),
					with(any(Predicate.class)));
			will(returnValue(record));
		}});
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

	private byte[] createPayload() throws Exception {
		ByteArrayOutputStream payload = new ByteArrayOutputStream();
		while (payload.size() + UniqueId.LENGTH <= MAX_RECORD_PAYLOAD_BYTES) {
			payload.write(getRandomId());
		}
		return payload.toByteArray();
	}
}

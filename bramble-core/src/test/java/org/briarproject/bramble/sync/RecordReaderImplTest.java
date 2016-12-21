package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.sync.Ack;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.util.ByteUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.briarproject.bramble.api.sync.RecordTypes.ACK;
import static org.briarproject.bramble.api.sync.RecordTypes.OFFER;
import static org.briarproject.bramble.api.sync.RecordTypes.REQUEST;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_IDS;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_RECORD_PAYLOAD_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.PROTOCOL_VERSION;
import static org.briarproject.bramble.api.sync.SyncConstants.RECORD_HEADER_LENGTH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecordReaderImplTest extends BrambleMockTestCase {

	private final MessageFactory messageFactory =
			context.mock(MessageFactory.class);

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfAckIsTooLarge() throws Exception {
		byte[] b = createAck(true);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		RecordReaderImpl reader = new RecordReaderImpl(messageFactory, in);
		reader.readAck();
	}

	@Test
	public void testNoFormatExceptionIfAckIsMaximumSize() throws Exception {
		byte[] b = createAck(false);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		RecordReaderImpl reader = new RecordReaderImpl(messageFactory, in);
		reader.readAck();
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfAckIsEmpty() throws Exception {
		byte[] b = createEmptyAck();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		RecordReaderImpl reader = new RecordReaderImpl(messageFactory, in);
		reader.readAck();
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfOfferIsTooLarge() throws Exception {
		byte[] b = createOffer(true);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		RecordReaderImpl reader = new RecordReaderImpl(messageFactory, in);
		reader.readOffer();
	}

	@Test
	public void testNoFormatExceptionIfOfferIsMaximumSize() throws Exception {
		byte[] b = createOffer(false);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		RecordReaderImpl reader = new RecordReaderImpl(messageFactory, in);
		reader.readOffer();
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfOfferIsEmpty() throws Exception {
		byte[] b = createEmptyOffer();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		RecordReaderImpl reader = new RecordReaderImpl(messageFactory, in);
		reader.readOffer();
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfRequestIsTooLarge() throws Exception {
		byte[] b = createRequest(true);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		RecordReaderImpl reader = new RecordReaderImpl(messageFactory, in);
		reader.readRequest();
	}

	@Test
	public void testNoFormatExceptionIfRequestIsMaximumSize() throws Exception {
		byte[] b = createRequest(false);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		RecordReaderImpl reader = new RecordReaderImpl(messageFactory, in);
		reader.readRequest();
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfRequestIsEmpty() throws Exception {
		byte[] b = createEmptyRequest();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		RecordReaderImpl reader = new RecordReaderImpl(messageFactory, in);
		reader.readRequest();
	}

	@Test
	public void testEofReturnsTrueWhenAtEndOfStream() throws Exception {
		ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
		RecordReaderImpl reader = new RecordReaderImpl(messageFactory, in);
		assertTrue(reader.eof());
	}

	@Test
	public void testEofReturnsFalseWhenNotAtEndOfStream() throws Exception {
		byte[] b = createAck(false);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		RecordReaderImpl reader = new RecordReaderImpl(messageFactory, in);
		assertFalse(reader.eof());
	}

	@Test(expected = FormatException.class)
	public void testThrowsExceptionIfHeaderIsTooShort() throws Exception {
		byte[] b = new byte[RECORD_HEADER_LENGTH - 1];
		b[0] = PROTOCOL_VERSION;
		b[1] = ACK;
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		RecordReaderImpl reader = new RecordReaderImpl(messageFactory, in);
		reader.eof();
	}

	@Test(expected = FormatException.class)
	public void testThrowsExceptionIfPayloadIsTooShort() throws Exception {
		int payloadLength = 123;
		byte[] b = new byte[RECORD_HEADER_LENGTH + payloadLength - 1];
		b[0] = PROTOCOL_VERSION;
		b[1] = ACK;
		ByteUtils.writeUint16(payloadLength, b, 2);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		RecordReaderImpl reader = new RecordReaderImpl(messageFactory, in);
		reader.eof();
	}

	@Test(expected = FormatException.class)
	public void testThrowsExceptionIfProtocolVersionIsUnrecognised()
			throws Exception {
		byte version = (byte) (PROTOCOL_VERSION + 1);
		byte[] b = createRecord(version, ACK, new byte[0]);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		RecordReaderImpl reader = new RecordReaderImpl(messageFactory, in);
		reader.eof();
	}

	@Test(expected = FormatException.class)
	public void testThrowsExceptionIfPayloadIsTooLong() throws Exception {
		byte[] payload = new byte[MAX_RECORD_PAYLOAD_LENGTH + 1];
		byte[] b = createRecord(PROTOCOL_VERSION, ACK, payload);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		RecordReaderImpl reader = new RecordReaderImpl(messageFactory, in);
		reader.eof();
	}

	@Test
	public void testSkipsUnrecognisedRecordTypes() throws Exception {
		byte[] skip1 = createRecord(PROTOCOL_VERSION, (byte) (REQUEST + 1),
				new byte[123]);
		byte[] skip2 = createRecord(PROTOCOL_VERSION, (byte) (REQUEST + 2),
				new byte[0]);
		byte[] ack = createAck(false);
		ByteArrayOutputStream input = new ByteArrayOutputStream();
		input.write(skip1);
		input.write(skip2);
		input.write(ack);
		ByteArrayInputStream in = new ByteArrayInputStream(input.toByteArray());
		RecordReaderImpl reader = new RecordReaderImpl(messageFactory, in);
		assertTrue(reader.hasAck());
		Ack a = reader.readAck();
		assertEquals(MAX_MESSAGE_IDS, a.getMessageIds().size());
	}

	private byte[] createAck(boolean tooBig) throws Exception {
		return createRecord(PROTOCOL_VERSION, ACK, createPayload(tooBig));
	}

	private byte[] createEmptyAck() throws Exception {
		return createRecord(PROTOCOL_VERSION, ACK, new byte[0]);
	}

	private byte[] createOffer(boolean tooBig) throws Exception {
		return createRecord(PROTOCOL_VERSION, OFFER, createPayload(tooBig));
	}

	private byte[] createEmptyOffer() throws Exception {
		return createRecord(PROTOCOL_VERSION, OFFER, new byte[0]);
	}

	private byte[] createRequest(boolean tooBig) throws Exception {
		return createRecord(PROTOCOL_VERSION, REQUEST, createPayload(tooBig));
	}

	private byte[] createEmptyRequest() throws Exception {
		return createRecord(PROTOCOL_VERSION, REQUEST, new byte[0]);
	}

	private byte[] createRecord(byte version, byte type, byte[] payload) {
		byte[] b = new byte[RECORD_HEADER_LENGTH + payload.length];
		b[0] = version;
		b[1] = type;
		ByteUtils.writeUint16(payload.length, b, 2);
		System.arraycopy(payload, 0, b, RECORD_HEADER_LENGTH, payload.length);
		return b;
	}

	private byte[] createPayload(boolean tooBig) throws Exception {
		ByteArrayOutputStream payload = new ByteArrayOutputStream();
		while (payload.size() + UniqueId.LENGTH <= MAX_RECORD_PAYLOAD_LENGTH) {
			payload.write(TestUtils.getRandomId());
		}
		if (tooBig) payload.write(TestUtils.getRandomId());
		assertEquals(tooBig, payload.size() > MAX_RECORD_PAYLOAD_LENGTH);
		return payload.toByteArray();
	}
}

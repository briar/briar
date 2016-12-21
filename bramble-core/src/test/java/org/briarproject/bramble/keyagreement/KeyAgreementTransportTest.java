package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.keyagreement.KeyAgreementConnection;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.util.ByteUtils;
import org.jmock.Expectations;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.PROTOCOL_VERSION;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.RECORD_HEADER_LENGTH;
import static org.briarproject.bramble.api.keyagreement.RecordTypes.ABORT;
import static org.briarproject.bramble.api.keyagreement.RecordTypes.CONFIRM;
import static org.briarproject.bramble.api.keyagreement.RecordTypes.KEY;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class KeyAgreementTransportTest extends BrambleMockTestCase {

	private final DuplexTransportConnection duplexTransportConnection =
			context.mock(DuplexTransportConnection.class);
	private final TransportConnectionReader transportConnectionReader =
			context.mock(TransportConnectionReader.class);
	private final TransportConnectionWriter transportConnectionWriter =
			context.mock(TransportConnectionWriter.class);

	private final TransportId transportId = new TransportId("test");
	private final KeyAgreementConnection keyAgreementConnection =
			new KeyAgreementConnection(duplexTransportConnection, transportId);

	private ByteArrayInputStream inputStream;
	private ByteArrayOutputStream outputStream;
	private KeyAgreementTransport kat;

	@Test
	public void testSendKey() throws Exception {
		setup(new byte[0]);
		byte[] key = TestUtils.getRandomBytes(123);
		kat.sendKey(key);
		assertRecordSent(KEY, key);
	}

	@Test
	public void testSendConfirm() throws Exception {
		setup(new byte[0]);
		byte[] confirm = TestUtils.getRandomBytes(123);
		kat.sendConfirm(confirm);
		assertRecordSent(CONFIRM, confirm);
	}

	@Test
	public void testSendAbortWithException() throws Exception {
		setup(new byte[0]);
		context.checking(new Expectations() {{
			oneOf(transportConnectionReader).dispose(true, true);
			oneOf(transportConnectionWriter).dispose(true);
		}});
		kat.sendAbort(true);
		assertRecordSent(ABORT, new byte[0]);
	}

	@Test
	public void testSendAbortWithoutException() throws Exception {
		setup(new byte[0]);
		context.checking(new Expectations() {{
			oneOf(transportConnectionReader).dispose(false, true);
			oneOf(transportConnectionWriter).dispose(false);
		}});
		kat.sendAbort(false);
		assertRecordSent(ABORT, new byte[0]);
	}

	@Test(expected = AbortException.class)
	public void testReceiveKeyThrowsExceptionIfAtEndOfStream()
			throws Exception {
		setup(new byte[0]);
		kat.receiveKey();
	}

	@Test(expected = AbortException.class)
	public void testReceiveKeyThrowsExceptionIfHeaderIsTooShort()
			throws Exception {
		byte[] input = new byte[RECORD_HEADER_LENGTH - 1];
		input[0] = PROTOCOL_VERSION;
		input[1] = KEY;
		setup(input);
		kat.receiveKey();
	}

	@Test(expected = AbortException.class)
	public void testReceiveKeyThrowsExceptionIfPayloadIsTooShort()
			throws Exception {
		int payloadLength = 123;
		byte[] input = new byte[RECORD_HEADER_LENGTH + payloadLength - 1];
		input[0] = PROTOCOL_VERSION;
		input[1] = KEY;
		ByteUtils.writeUint16(payloadLength, input, 2);
		setup(input);
		kat.receiveKey();
	}

	@Test(expected = AbortException.class)
	public void testReceiveKeyThrowsExceptionIfProtocolVersionIsUnrecognised()
			throws Exception {
		setup(createRecord((byte) (PROTOCOL_VERSION + 1), KEY, new byte[123]));
		kat.receiveKey();
	}

	@Test(expected = AbortException.class)
	public void testReceiveKeyThrowsExceptionIfAbortIsReceived()
			throws Exception {
		setup(createRecord(PROTOCOL_VERSION, ABORT, new byte[0]));
		kat.receiveKey();
	}

	@Test(expected = AbortException.class)
	public void testReceiveKeyThrowsExceptionIfConfirmIsReceived()
			throws Exception {
		setup(createRecord(PROTOCOL_VERSION, CONFIRM, new byte[123]));
		kat.receiveKey();
	}

	@Test
	public void testReceiveKeySkipsUnrecognisedRecordTypes() throws Exception {
		byte[] skip1 = createRecord(PROTOCOL_VERSION, (byte) (ABORT + 1),
				new byte[123]);
		byte[] skip2 = createRecord(PROTOCOL_VERSION, (byte) (ABORT + 2),
				new byte[0]);
		byte[] payload = TestUtils.getRandomBytes(123);
		byte[] key = createRecord(PROTOCOL_VERSION, KEY, payload);
		ByteArrayOutputStream input = new ByteArrayOutputStream();
		input.write(skip1);
		input.write(skip2);
		input.write(key);
		setup(input.toByteArray());
		assertArrayEquals(payload, kat.receiveKey());
	}

	@Test(expected = AbortException.class)
	public void testReceiveConfirmThrowsExceptionIfAtEndOfStream()
			throws Exception {
		setup(new byte[0]);
		kat.receiveConfirm();
	}

	@Test(expected = AbortException.class)
	public void testReceiveConfirmThrowsExceptionIfHeaderIsTooShort()
			throws Exception {
		byte[] input = new byte[RECORD_HEADER_LENGTH - 1];
		input[0] = PROTOCOL_VERSION;
		input[1] = CONFIRM;
		setup(input);
		kat.receiveConfirm();
	}

	@Test(expected = AbortException.class)
	public void testReceiveConfirmThrowsExceptionIfPayloadIsTooShort()
			throws Exception {
		int payloadLength = 123;
		byte[] input = new byte[RECORD_HEADER_LENGTH + payloadLength - 1];
		input[0] = PROTOCOL_VERSION;
		input[1] = CONFIRM;
		ByteUtils.writeUint16(payloadLength, input, 2);
		setup(input);
		kat.receiveConfirm();
	}

	@Test(expected = AbortException.class)
	public void testReceiveConfirmThrowsExceptionIfProtocolVersionIsUnrecognised()
			throws Exception {
		setup(createRecord((byte) (PROTOCOL_VERSION + 1), CONFIRM,
				new byte[123]));
		kat.receiveConfirm();
	}

	@Test(expected = AbortException.class)
	public void testReceiveConfirmThrowsExceptionIfAbortIsReceived()
			throws Exception {
		setup(createRecord(PROTOCOL_VERSION, ABORT, new byte[0]));
		kat.receiveConfirm();
	}

	@Test(expected = AbortException.class)
	public void testReceiveKeyThrowsExceptionIfKeyIsReceived()
			throws Exception {
		setup(createRecord(PROTOCOL_VERSION, KEY, new byte[123]));
		kat.receiveConfirm();
	}

	@Test
	public void testReceiveConfirmSkipsUnrecognisedRecordTypes()
			throws Exception {
		byte[] skip1 = createRecord(PROTOCOL_VERSION, (byte) (ABORT + 1),
				new byte[123]);
		byte[] skip2 = createRecord(PROTOCOL_VERSION, (byte) (ABORT + 2),
				new byte[0]);
		byte[] payload = TestUtils.getRandomBytes(123);
		byte[] confirm = createRecord(PROTOCOL_VERSION, CONFIRM, payload);
		ByteArrayOutputStream input = new ByteArrayOutputStream();
		input.write(skip1);
		input.write(skip2);
		input.write(confirm);
		setup(input.toByteArray());
		assertArrayEquals(payload, kat.receiveConfirm());
	}

	private void setup(byte[] input) throws Exception {
		inputStream = new ByteArrayInputStream(input);
		outputStream = new ByteArrayOutputStream();
		context.checking(new Expectations() {{
			allowing(duplexTransportConnection).getReader();
			will(returnValue(transportConnectionReader));
			allowing(transportConnectionReader).getInputStream();
			will(returnValue(inputStream));
			allowing(duplexTransportConnection).getWriter();
			will(returnValue(transportConnectionWriter));
			allowing(transportConnectionWriter).getOutputStream();
			will(returnValue(outputStream));
		}});
		kat = new KeyAgreementTransport(keyAgreementConnection);
	}

	private void assertRecordSent(byte expectedType, byte[] expectedPayload) {
		byte[] output = outputStream.toByteArray();
		assertEquals(RECORD_HEADER_LENGTH + expectedPayload.length,
				output.length);
		assertEquals(PROTOCOL_VERSION, output[0]);
		assertEquals(expectedType, output[1]);
		assertEquals(expectedPayload.length, ByteUtils.readUint16(output, 2));
		byte[] payload = new byte[output.length - RECORD_HEADER_LENGTH];
		System.arraycopy(output, RECORD_HEADER_LENGTH, payload, 0,
				payload.length);
		assertArrayEquals(expectedPayload, payload);
	}

	private byte[] createRecord(byte version, byte type, byte[] payload) {
		byte[] b = new byte[RECORD_HEADER_LENGTH + payload.length];
		b[0] = version;
		b[1] = type;
		ByteUtils.writeUint16(payload.length, b, 2);
		System.arraycopy(payload, 0, b, RECORD_HEADER_LENGTH, payload.length);
		return b;
	}
}

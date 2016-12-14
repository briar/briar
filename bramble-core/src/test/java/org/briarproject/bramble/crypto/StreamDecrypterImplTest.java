package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.util.ByteUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static junit.framework.Assert.assertEquals;
import static org.briarproject.bramble.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.MAX_PAYLOAD_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.STREAM_HEADER_IV_LENGTH;
import static org.briarproject.bramble.util.ByteUtils.INT_16_BYTES;
import static org.junit.Assert.assertArrayEquals;

public class StreamDecrypterImplTest extends BrambleTestCase {

	private final AuthenticatedCipher cipher;
	private final SecretKey streamHeaderKey, frameKey;
	private final byte[] streamHeaderIv, payload;
	private final int payloadLength = 123, paddingLength = 234;
	private final long streamNumber = 1234;

	public StreamDecrypterImplTest() {
		cipher = new TestAuthenticatedCipher(); // Null cipher
		streamHeaderKey = TestUtils.getSecretKey();
		frameKey = TestUtils.getSecretKey();
		streamHeaderIv = TestUtils.getRandomBytes(STREAM_HEADER_IV_LENGTH);
		payload = TestUtils.getRandomBytes(payloadLength);
	}

	@Test
	public void testReadValidFrames() throws Exception {
		byte[] frameHeader = new byte[FRAME_HEADER_LENGTH];
		FrameEncoder.encodeHeader(frameHeader, false, payloadLength,
				paddingLength);

		byte[] frameHeader1 = new byte[FRAME_HEADER_LENGTH];
		int payloadLength1 = 345, paddingLength1 = 456;
		FrameEncoder.encodeHeader(frameHeader1, true, payloadLength1,
				paddingLength1);
		byte[] payload1 = TestUtils.getRandomBytes(payloadLength1);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(streamHeaderIv);
		out.write(frameKey.getBytes());
		out.write(new byte[MAC_LENGTH]);
		out.write(frameHeader);
		out.write(payload);
		out.write(new byte[paddingLength]);
		out.write(new byte[MAC_LENGTH]);
		out.write(frameHeader1);
		out.write(payload1);
		out.write(new byte[paddingLength1]);
		out.write(new byte[MAC_LENGTH]);

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		StreamDecrypterImpl s = new StreamDecrypterImpl(in, cipher,
				streamNumber, streamHeaderKey);

		// Read the first frame
		byte[] buffer = new byte[MAX_PAYLOAD_LENGTH];
		assertEquals(payloadLength, s.readFrame(buffer));
		assertArrayStartsWith(payload, buffer, payloadLength);

		// Read the second frame
		assertEquals(payloadLength1, s.readFrame(buffer));
		assertArrayStartsWith(payload1, buffer, payloadLength1);

		// End of stream
		assertEquals(-1, s.readFrame(buffer));
	}

	@Test(expected = IOException.class)
	public void testTruncatedFrameThrowsException() throws Exception {
		byte[] frameHeader = new byte[FRAME_HEADER_LENGTH];
		FrameEncoder.encodeHeader(frameHeader, false, payloadLength,
				paddingLength);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(streamHeaderIv);
		out.write(frameKey.getBytes());
		out.write(new byte[MAC_LENGTH]);
		out.write(frameHeader);
		out.write(payload);
		out.write(new byte[paddingLength]);
		out.write(new byte[MAC_LENGTH - 1]); // Chop off the last byte

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		StreamDecrypterImpl s = new StreamDecrypterImpl(in, cipher,
				streamNumber, streamHeaderKey);

		// Try to read the truncated frame
		byte[] buffer = new byte[MAX_PAYLOAD_LENGTH];
		s.readFrame(buffer);
	}

	@Test(expected = IOException.class)
	public void testInvalidPayloadAndPaddingLengthThrowsException()
			throws Exception {
		byte[] frameHeader = new byte[FRAME_HEADER_LENGTH];
		// The payload length plus padding length is invalid
		int payloadLength = MAX_PAYLOAD_LENGTH - 1, paddingLength = 2;
		ByteUtils.writeUint16(payloadLength, frameHeader, 0);
		ByteUtils.writeUint16(paddingLength, frameHeader, INT_16_BYTES);
		byte[] payload = TestUtils.getRandomBytes(payloadLength);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(streamHeaderIv);
		out.write(frameKey.getBytes());
		out.write(new byte[MAC_LENGTH]);
		out.write(frameHeader);
		out.write(payload);
		out.write(new byte[paddingLength]);
		out.write(new byte[MAC_LENGTH]);

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		StreamDecrypterImpl s = new StreamDecrypterImpl(in, cipher,
				streamNumber, streamHeaderKey);

		// Try to read the invalid frame
		byte[] buffer = new byte[MAX_PAYLOAD_LENGTH];
		s.readFrame(buffer);
	}

	@Test(expected = IOException.class)
	public void testNonZeroPaddingThrowsException() throws Exception {
		byte[] frameHeader = new byte[FRAME_HEADER_LENGTH];
		FrameEncoder.encodeHeader(frameHeader, false, payloadLength,
				paddingLength);
		// Set one of the padding bytes non-zero
		byte[] padding = new byte[paddingLength];
		padding[paddingLength - 1] = 1;

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(streamHeaderIv);
		out.write(frameKey.getBytes());
		out.write(new byte[MAC_LENGTH]);
		out.write(frameHeader);
		out.write(payload);
		out.write(padding);
		out.write(new byte[MAC_LENGTH]);

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		StreamDecrypterImpl s = new StreamDecrypterImpl(in, cipher,
				streamNumber, streamHeaderKey);

		// Try to read the invalid frame
		byte[] buffer = new byte[MAX_PAYLOAD_LENGTH];
		s.readFrame(buffer);
	}

	@Test
	public void testCannotReadBeyondFinalFrame() throws Exception {
		byte[] frameHeader = new byte[FRAME_HEADER_LENGTH];
		FrameEncoder.encodeHeader(frameHeader, true, payloadLength,
				paddingLength);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(streamHeaderIv);
		out.write(frameKey.getBytes());
		out.write(new byte[MAC_LENGTH]);
		out.write(frameHeader);
		out.write(payload);
		out.write(new byte[paddingLength]);
		out.write(new byte[MAC_LENGTH]);
		// Add some data beyond the final frame
		out.write(new byte[1024]);

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		StreamDecrypterImpl s = new StreamDecrypterImpl(in, cipher,
				streamNumber, streamHeaderKey);

		// Read the first frame
		byte[] buffer = new byte[MAX_PAYLOAD_LENGTH];
		assertEquals(payloadLength, s.readFrame(buffer));
		assertArrayStartsWith(payload, buffer, payloadLength);

		// End of stream
		assertEquals(-1, s.readFrame(buffer));

		// Yup, definitely end of stream
		assertEquals(-1, s.readFrame(buffer));
	}

	private static void assertArrayStartsWith(byte[] expected, byte[] actual,
			int len) {
		byte[] prefix = new byte[len];
		System.arraycopy(actual, 0, prefix, 0, len);
		assertArrayEquals(expected, prefix);
	}
}

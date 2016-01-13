package org.briarproject.crypto;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.util.ByteUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static junit.framework.Assert.assertEquals;
import static org.briarproject.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_PAYLOAD_LENGTH;
import static org.briarproject.api.transport.TransportConstants.STREAM_HEADER_IV_LENGTH;
import static org.briarproject.util.ByteUtils.INT_16_BYTES;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

public class StreamDecrypterImplTest extends BriarTestCase {

	private final AuthenticatedCipher cipher;
	private final SecretKey streamHeaderKey, frameKey;
	private final byte[] streamHeaderIv;
	private final Random random;

	public StreamDecrypterImplTest() {
		cipher = new TestAuthenticatedCipher(); // Null cipher
		streamHeaderKey = TestUtils.createSecretKey();
		frameKey = TestUtils.createSecretKey();
		streamHeaderIv = new byte[STREAM_HEADER_IV_LENGTH];
		random = new Random();
		random.nextBytes(streamHeaderIv);
	}

	@Test
	public void testReadValidFrames() throws Exception {
		byte[] frameHeader = new byte[FRAME_HEADER_LENGTH];
		int payloadLength = 123, paddingLength = 234;
		FrameEncoder.encodeHeader(frameHeader, false, payloadLength,
				paddingLength);
		byte[] payload = new byte[payloadLength];
		random.nextBytes(payload);

		byte[] frameHeader1 = new byte[FRAME_HEADER_LENGTH];
		int payloadLength1 = 345, paddingLength1 = 456;
		FrameEncoder.encodeHeader(frameHeader1, true, payloadLength1,
				paddingLength1);
		byte[] payload1 = new byte[payloadLength1];
		random.nextBytes(payload1);

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
				streamHeaderKey);

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
		int payloadLength = 123, paddingLength = 234;
		FrameEncoder.encodeHeader(frameHeader, false, payloadLength,
				paddingLength);
		byte[] payload = new byte[payloadLength];
		random.nextBytes(payload);
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
				streamHeaderKey);

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
		byte[] payload = new byte[payloadLength];
		random.nextBytes(payload);

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
				streamHeaderKey);

		// Try to read the invalid frame
		byte[] buffer = new byte[MAX_PAYLOAD_LENGTH];
		s.readFrame(buffer);
	}

	@Test(expected = IOException.class)
	public void testNonZeroPaddingThrowsException() throws Exception {
		byte[] frameHeader = new byte[FRAME_HEADER_LENGTH];
		int payloadLength = 123, paddingLength = 234;
		FrameEncoder.encodeHeader(frameHeader, false, payloadLength,
				paddingLength);
		byte[] payload = new byte[payloadLength];
		random.nextBytes(payload);
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
				streamHeaderKey);

		// Try to read the invalid frame
		byte[] buffer = new byte[MAX_PAYLOAD_LENGTH];
		s.readFrame(buffer);
	}

	@Test
	public void testCannotReadBeyondFinalFrame() throws Exception {
		byte[] frameHeader = new byte[FRAME_HEADER_LENGTH];
		int payloadLength = 123, paddingLength = 234;
		FrameEncoder.encodeHeader(frameHeader, true, payloadLength,
				paddingLength);
		byte[] payload = new byte[payloadLength];
		random.nextBytes(payload);

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
				streamHeaderKey);

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

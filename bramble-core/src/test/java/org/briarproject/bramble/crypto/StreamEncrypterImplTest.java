package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestUtils;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.briarproject.bramble.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.MAX_PAYLOAD_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.STREAM_HEADER_IV_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.STREAM_HEADER_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class StreamEncrypterImplTest extends BrambleTestCase {

	private final AuthenticatedCipher cipher;
	private final SecretKey streamHeaderKey, frameKey;
	private final byte[] tag, streamHeaderIv, payload;
	private final long streamNumber = 1234;
	private final int payloadLength = 123, paddingLength = 234;

	public StreamEncrypterImplTest() {
		cipher = new TestAuthenticatedCipher(); // Null cipher
		streamHeaderKey = TestUtils.getSecretKey();
		frameKey = TestUtils.getSecretKey();
		tag = TestUtils.getRandomBytes(TAG_LENGTH);
		streamHeaderIv = TestUtils.getRandomBytes(STREAM_HEADER_IV_LENGTH);
		payload = TestUtils.getRandomBytes(payloadLength);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRejectsNegativePayloadLength() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher,
				streamNumber, tag, streamHeaderIv, streamHeaderKey, frameKey);

		s.writeFrame(payload, -1, 0, false);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRejectsNegativePaddingLength() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher,
				streamNumber, tag, streamHeaderIv, streamHeaderKey, frameKey);

		s.writeFrame(payload, 0, -1, false);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRejectsMaxPayloadPlusPadding() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher,
				streamNumber, tag, streamHeaderIv, streamHeaderKey, frameKey);

		byte[] bigPayload = new byte[MAX_PAYLOAD_LENGTH + 1];
		s.writeFrame(bigPayload, MAX_PAYLOAD_LENGTH, 1, false);
	}

	@Test
	public void testAcceptsMaxPayloadIncludingPadding() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher,
				streamNumber, tag, streamHeaderIv, streamHeaderKey, frameKey);

		byte[] bigPayload = new byte[MAX_PAYLOAD_LENGTH];
		s.writeFrame(bigPayload, MAX_PAYLOAD_LENGTH - 1, 1, false);
		assertEquals(TAG_LENGTH + STREAM_HEADER_LENGTH + MAX_FRAME_LENGTH,
				out.size());
	}

	@Test
	public void testAcceptsMaxPayloadWithoutPadding() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher,
				streamNumber, tag, streamHeaderIv, streamHeaderKey, frameKey);

		byte[] bigPayload = new byte[MAX_PAYLOAD_LENGTH];
		s.writeFrame(bigPayload, MAX_PAYLOAD_LENGTH, 0, false);
		assertEquals(TAG_LENGTH + STREAM_HEADER_LENGTH + MAX_FRAME_LENGTH,
				out.size());
	}

	@Test
	public void testWriteUnpaddedNonFinalFrameWithTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher,
				streamNumber, tag, streamHeaderIv, streamHeaderKey, frameKey);

		s.writeFrame(payload, payloadLength, 0, false);

		// Expect the tag, stream header, frame header, payload and MAC
		ByteArrayOutputStream expected = new ByteArrayOutputStream();
		expected.write(tag);
		expected.write(streamHeaderIv);
		expected.write(frameKey.getBytes());
		expected.write(new byte[MAC_LENGTH]);
		byte[] expectedFrameHeader = new byte[FRAME_HEADER_LENGTH];
		FrameEncoder.encodeHeader(expectedFrameHeader, false, payloadLength, 0);
		expected.write(expectedFrameHeader);
		expected.write(payload);
		expected.write(new byte[MAC_LENGTH]);

		assertArrayEquals(expected.toByteArray(), out.toByteArray());
	}

	@Test
	public void testWriteUnpaddedFinalFrameWithTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher,
				streamNumber, tag, streamHeaderIv, streamHeaderKey, frameKey);

		s.writeFrame(payload, payloadLength, 0, true);

		// Expect the tag, stream header, frame header, payload and MAC
		ByteArrayOutputStream expected = new ByteArrayOutputStream();
		expected.write(tag);
		expected.write(streamHeaderIv);
		expected.write(frameKey.getBytes());
		expected.write(new byte[MAC_LENGTH]);
		byte[] expectedFrameHeader = new byte[FRAME_HEADER_LENGTH];
		FrameEncoder.encodeHeader(expectedFrameHeader, true, payloadLength, 0);
		expected.write(expectedFrameHeader);
		expected.write(payload);
		expected.write(new byte[MAC_LENGTH]);

		assertArrayEquals(expected.toByteArray(), out.toByteArray());
	}

	@Test
	public void testWriteUnpaddedNonFinalFrameWithoutTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher,
				streamNumber, null, streamHeaderIv, streamHeaderKey, frameKey);

		s.writeFrame(payload, payloadLength, 0, false);

		// Expect the stream header, frame header, payload and MAC
		ByteArrayOutputStream expected = new ByteArrayOutputStream();
		expected.write(streamHeaderIv);
		expected.write(frameKey.getBytes());
		expected.write(new byte[MAC_LENGTH]);
		byte[] expectedFrameHeader = new byte[FRAME_HEADER_LENGTH];
		FrameEncoder.encodeHeader(expectedFrameHeader, false, payloadLength, 0);
		expected.write(expectedFrameHeader);
		expected.write(payload);
		expected.write(new byte[MAC_LENGTH]);

		assertArrayEquals(expected.toByteArray(), out.toByteArray());
	}

	@Test
	public void testWriteUnpaddedFinalFrameWithoutTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher,
				streamNumber, null, streamHeaderIv, streamHeaderKey, frameKey);

		s.writeFrame(payload, payloadLength, 0, true);

		// Expect the stream header, frame header, payload and MAC
		ByteArrayOutputStream expected = new ByteArrayOutputStream();
		expected.write(streamHeaderIv);
		expected.write(frameKey.getBytes());
		expected.write(new byte[MAC_LENGTH]);
		byte[] expectedFrameHeader = new byte[FRAME_HEADER_LENGTH];
		FrameEncoder.encodeHeader(expectedFrameHeader, true, payloadLength, 0);
		expected.write(expectedFrameHeader);
		expected.write(payload);
		expected.write(new byte[MAC_LENGTH]);

		assertArrayEquals(expected.toByteArray(), out.toByteArray());
	}

	@Test
	public void testWritePaddedNonFinalFrameWithTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher,
				streamNumber, tag, streamHeaderIv, streamHeaderKey, frameKey);

		s.writeFrame(payload, payloadLength, paddingLength, false);

		// Expect the tag, stream header, frame header, payload, padding and MAC
		ByteArrayOutputStream expected = new ByteArrayOutputStream();
		expected.write(tag);
		expected.write(streamHeaderIv);
		expected.write(frameKey.getBytes());
		expected.write(new byte[MAC_LENGTH]);
		byte[] expectedFrameHeader = new byte[FRAME_HEADER_LENGTH];
		FrameEncoder.encodeHeader(expectedFrameHeader, false, payloadLength,
				paddingLength);
		expected.write(expectedFrameHeader);
		expected.write(payload);
		expected.write(new byte[paddingLength]);
		expected.write(new byte[MAC_LENGTH]);

		assertArrayEquals(expected.toByteArray(), out.toByteArray());
	}

	@Test
	public void testWritePaddedFinalFrameWithTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher,
				streamNumber, tag, streamHeaderIv, streamHeaderKey, frameKey);

		s.writeFrame(payload, payloadLength, paddingLength, true);

		// Expect the tag, stream header, frame header, payload, padding and MAC
		ByteArrayOutputStream expected = new ByteArrayOutputStream();
		expected.write(tag);
		expected.write(streamHeaderIv);
		expected.write(frameKey.getBytes());
		expected.write(new byte[MAC_LENGTH]);
		byte[] expectedFrameHeader = new byte[FRAME_HEADER_LENGTH];
		FrameEncoder.encodeHeader(expectedFrameHeader, true, payloadLength,
				paddingLength);
		expected.write(expectedFrameHeader);
		expected.write(payload);
		expected.write(new byte[paddingLength]);
		expected.write(new byte[MAC_LENGTH]);

		assertArrayEquals(expected.toByteArray(), out.toByteArray());
	}

	@Test
	public void testWritePaddedNonFinalFrameWithoutTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher,
				streamNumber, null, streamHeaderIv, streamHeaderKey, frameKey);

		s.writeFrame(payload, payloadLength, paddingLength, false);

		// Expect the stream header, frame header, payload, padding and MAC
		ByteArrayOutputStream expected = new ByteArrayOutputStream();
		expected.write(streamHeaderIv);
		expected.write(frameKey.getBytes());
		expected.write(new byte[MAC_LENGTH]);
		byte[] expectedFrameHeader = new byte[FRAME_HEADER_LENGTH];
		FrameEncoder.encodeHeader(expectedFrameHeader, false, payloadLength,
				paddingLength);
		expected.write(expectedFrameHeader);
		expected.write(payload);
		expected.write(new byte[paddingLength]);
		expected.write(new byte[MAC_LENGTH]);

		assertArrayEquals(expected.toByteArray(), out.toByteArray());
	}

	@Test
	public void testWritePaddedFinalFrameWithoutTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher,
				streamNumber, null, streamHeaderIv, streamHeaderKey, frameKey);

		s.writeFrame(payload, payloadLength, paddingLength, true);

		// Expect the stream header, frame header, payload, padding and MAC
		ByteArrayOutputStream expected = new ByteArrayOutputStream();
		expected.write(streamHeaderIv);
		expected.write(frameKey.getBytes());
		expected.write(new byte[MAC_LENGTH]);
		byte[] expectedFrameHeader = new byte[FRAME_HEADER_LENGTH];
		FrameEncoder.encodeHeader(expectedFrameHeader, true, payloadLength,
				paddingLength);
		expected.write(expectedFrameHeader);
		expected.write(payload);
		expected.write(new byte[paddingLength]);
		expected.write(new byte[MAC_LENGTH]);

		assertArrayEquals(expected.toByteArray(), out.toByteArray());
	}

	@Test
	public void testWriteTwoFramesWithTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher,
				streamNumber, tag, streamHeaderIv, streamHeaderKey, frameKey);
		int payloadLength1 = 345, paddingLength1 = 456;
		byte[] payload1 = TestUtils.getRandomBytes(payloadLength1);

		s.writeFrame(payload, payloadLength, paddingLength, false);
		s.writeFrame(payload1, payloadLength1, paddingLength1, true);

		// Expect the tag, stream header, first frame header, payload, padding,
		// MAC, second frame header, payload, padding, MAC
		ByteArrayOutputStream expected = new ByteArrayOutputStream();
		expected.write(tag);
		expected.write(streamHeaderIv);
		expected.write(frameKey.getBytes());
		expected.write(new byte[MAC_LENGTH]);
		byte[] expectedFrameHeader = new byte[FRAME_HEADER_LENGTH];
		FrameEncoder.encodeHeader(expectedFrameHeader, false, payloadLength,
				paddingLength);
		expected.write(expectedFrameHeader);
		expected.write(payload);
		expected.write(new byte[paddingLength]);
		expected.write(new byte[MAC_LENGTH]);
		byte[] expectedFrameHeader1 = new byte[FRAME_HEADER_LENGTH];
		FrameEncoder.encodeHeader(expectedFrameHeader1, true, payloadLength1,
				paddingLength1);
		expected.write(expectedFrameHeader1);
		expected.write(payload1);
		expected.write(new byte[paddingLength1]);
		expected.write(new byte[MAC_LENGTH]);

		assertArrayEquals(expected.toByteArray(), out.toByteArray());
	}

	@Test
	public void testFlushWritesTagAndStreamHeaderIfNotAlreadyWritten()
			throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher,
				streamNumber, tag, streamHeaderIv, streamHeaderKey, frameKey);

		// Flush the stream once
		s.flush();

		// Expect the tag and stream header
		ByteArrayOutputStream expected = new ByteArrayOutputStream();
		expected.write(tag);
		expected.write(streamHeaderIv);
		expected.write(frameKey.getBytes());
		expected.write(new byte[MAC_LENGTH]);

		assertArrayEquals(expected.toByteArray(), out.toByteArray());
	}

	@Test
	public void testFlushDoesNotWriteTagOrStreamHeaderIfAlreadyWritten()
			throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher,
				streamNumber, tag, streamHeaderIv, streamHeaderKey, frameKey);

		// Flush the stream twice
		s.flush();
		s.flush();

		// Expect the tag and stream header
		ByteArrayOutputStream expected = new ByteArrayOutputStream();
		expected.write(tag);
		expected.write(streamHeaderIv);
		expected.write(frameKey.getBytes());
		expected.write(new byte[MAC_LENGTH]);

		assertArrayEquals(expected.toByteArray(), out.toByteArray());
	}

	@Test
	public void testFlushDoesNotWriteTagIfNull() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher,
				streamNumber, null, streamHeaderIv, streamHeaderKey, frameKey);

		// Flush the stream once
		s.flush();

		// Expect the stream header
		ByteArrayOutputStream expected = new ByteArrayOutputStream();
		expected.write(streamHeaderIv);
		expected.write(frameKey.getBytes());
		expected.write(new byte[MAC_LENGTH]);

		assertArrayEquals(expected.toByteArray(), out.toByteArray());
	}
}

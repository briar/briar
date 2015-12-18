package org.briarproject.crypto;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.crypto.SecretKey;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Random;

import static org.briarproject.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.api.transport.TransportConstants.STREAM_HEADER_IV_LENGTH;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;
import static org.junit.Assert.assertArrayEquals;

public class StreamEncrypterImplTest extends BriarTestCase {

	private final AuthenticatedCipher cipher;
	private final SecretKey streamHeaderKey, frameKey;
	private final byte[] tag, streamHeaderIv;
	private final Random random;

	public StreamEncrypterImplTest() {
		cipher = new TestAuthenticatedCipher(); // Null cipher
		streamHeaderKey = TestUtils.createSecretKey();
		frameKey = TestUtils.createSecretKey();
		tag = new byte[TAG_LENGTH];
		streamHeaderIv = new byte[STREAM_HEADER_IV_LENGTH];
		random = new Random();
		random.nextBytes(tag);
		random.nextBytes(streamHeaderIv);
	}

	@Test
	public void testWriteUnpaddedNonFinalFrameWithTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher, tag,
				streamHeaderIv, streamHeaderKey, frameKey);
		int payloadLength = 123;
		byte[] payload = new byte[payloadLength];
		random.nextBytes(payload);

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
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher, tag,
				streamHeaderIv, streamHeaderKey, frameKey);
		int payloadLength = 123;
		byte[] payload = new byte[payloadLength];
		random.nextBytes(payload);

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
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher, null,
				streamHeaderIv, streamHeaderKey, frameKey);
		int payloadLength = 123;
		byte[] payload = new byte[payloadLength];
		random.nextBytes(payload);

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
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher, null,
				streamHeaderIv, streamHeaderKey, frameKey);
		int payloadLength = 123;
		byte[] payload = new byte[payloadLength];
		random.nextBytes(payload);

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
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher, tag,
				streamHeaderIv, streamHeaderKey, frameKey);
		int payloadLength = 123, paddingLength = 234;
		byte[] payload = new byte[payloadLength];
		random.nextBytes(payload);

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
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher, tag,
				streamHeaderIv, streamHeaderKey, frameKey);
		int payloadLength = 123, paddingLength = 234;
		byte[] payload = new byte[payloadLength];
		random.nextBytes(payload);

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
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher, null,
				streamHeaderIv, streamHeaderKey, frameKey);
		int payloadLength = 123, paddingLength = 234;
		byte[] payload = new byte[payloadLength];
		random.nextBytes(payload);

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
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher, null,
				streamHeaderIv, streamHeaderKey, frameKey);
		int payloadLength = 123, paddingLength = 234;
		byte[] payload = new byte[payloadLength];
		random.nextBytes(payload);

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
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher, tag,
				streamHeaderIv, streamHeaderKey, frameKey);
		int payloadLength = 123, paddingLength = 234;
		byte[] payload = new byte[payloadLength];
		random.nextBytes(payload);
		int payloadLength1 = 345, paddingLength1 = 456;
		byte[] payload1 = new byte[payloadLength1];
		random.nextBytes(payload1);

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
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher, tag,
				streamHeaderIv, streamHeaderKey, frameKey);

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
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher, tag,
				streamHeaderIv, streamHeaderKey, frameKey);

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
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, cipher, null,
				streamHeaderIv, streamHeaderKey, frameKey);

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

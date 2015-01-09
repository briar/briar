package org.briarproject.crypto;

import static org.briarproject.api.transport.TransportConstants.HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.util.Random;

import org.briarproject.BriarTestCase;
import org.briarproject.api.crypto.SecretKey;
import org.junit.Test;

public class StreamEncrypterImplTest extends BriarTestCase {

	private final AuthenticatedCipher frameCipher;
	private final SecretKey frameKey;
	private final byte[] tag;

	public StreamEncrypterImplTest() {
		frameCipher = new TestAuthenticatedCipher();
		frameKey = new SecretKey(new byte[32]);
		tag = new byte[TAG_LENGTH];
		new Random().nextBytes(tag);
	}

	@Test
	public void testWriteUnpaddedNonFinalFrameWithTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, frameCipher,
				frameKey, tag);
		int payloadLength = 123;
		byte[] payload = new byte[payloadLength];
		new Random().nextBytes(payload);

		s.writeFrame(payload, payloadLength, 0, false);

		byte[] header = new byte[HEADER_LENGTH];
		FrameEncoder.encodeHeader(header, false, payloadLength, 0);
		byte[] expected = new byte[TAG_LENGTH + HEADER_LENGTH + payloadLength
		                           + MAC_LENGTH];
		System.arraycopy(tag, 0, expected, 0, TAG_LENGTH);
		System.arraycopy(header, 0, expected, TAG_LENGTH, HEADER_LENGTH);
		System.arraycopy(payload, 0, expected, TAG_LENGTH + HEADER_LENGTH,
				payloadLength);
		assertArrayEquals(expected, out.toByteArray());
	}

	@Test
	public void testWriteUnpaddedFinalFrameWithTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, frameCipher,
				frameKey, tag);
		int payloadLength = 123;
		byte[] payload = new byte[payloadLength];
		new Random().nextBytes(payload);

		s.writeFrame(payload, payloadLength, 0, true);

		byte[] header = new byte[HEADER_LENGTH];
		FrameEncoder.encodeHeader(header, true, payloadLength, 0);
		byte[] expected = new byte[TAG_LENGTH + HEADER_LENGTH + payloadLength
		                           + MAC_LENGTH];
		System.arraycopy(tag, 0, expected, 0, TAG_LENGTH);
		System.arraycopy(header, 0, expected, TAG_LENGTH, HEADER_LENGTH);
		System.arraycopy(payload, 0, expected, TAG_LENGTH + HEADER_LENGTH,
				payloadLength);
		assertArrayEquals(expected, out.toByteArray());
	}

	@Test
	public void testWriteUnpaddedNonFinalFrameWithoutTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, frameCipher,
				frameKey, null);
		int payloadLength = 123;
		byte[] payload = new byte[payloadLength];
		new Random().nextBytes(payload);

		s.writeFrame(payload, payloadLength, 0, false);

		byte[] header = new byte[HEADER_LENGTH];
		FrameEncoder.encodeHeader(header, false, payloadLength, 0);
		byte[] expected = new byte[HEADER_LENGTH + payloadLength + MAC_LENGTH];
		System.arraycopy(header, 0, expected, 0, HEADER_LENGTH);
		System.arraycopy(payload, 0, expected, HEADER_LENGTH, payloadLength);
		assertArrayEquals(expected, out.toByteArray());
	}

	@Test
	public void testWriteUnpaddedFinalFrameWithoutTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, frameCipher,
				frameKey, null);
		int payloadLength = 123;
		byte[] payload = new byte[payloadLength];
		new Random().nextBytes(payload);

		s.writeFrame(payload, payloadLength, 0, true);

		byte[] header = new byte[HEADER_LENGTH];
		FrameEncoder.encodeHeader(header, true, payloadLength, 0);
		byte[] expected = new byte[HEADER_LENGTH + payloadLength + MAC_LENGTH];
		System.arraycopy(header, 0, expected, 0, HEADER_LENGTH);
		System.arraycopy(payload, 0, expected, HEADER_LENGTH, payloadLength);
		assertArrayEquals(expected, out.toByteArray());
	}

	@Test
	public void testWritePaddedNonFinalFrameWithTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, frameCipher,
				frameKey, tag);
		int payloadLength = 123, paddingLength = 234;
		byte[] payload = new byte[payloadLength];
		new Random().nextBytes(payload);

		s.writeFrame(payload, payloadLength, paddingLength, false);

		byte[] header = new byte[HEADER_LENGTH];
		FrameEncoder.encodeHeader(header, false, payloadLength, paddingLength);
		byte[] expected = new byte[TAG_LENGTH + HEADER_LENGTH + payloadLength
		                           + paddingLength + MAC_LENGTH];
		System.arraycopy(tag, 0, expected, 0, TAG_LENGTH);
		System.arraycopy(header, 0, expected, TAG_LENGTH, HEADER_LENGTH);
		System.arraycopy(payload, 0, expected, TAG_LENGTH + HEADER_LENGTH,
				payloadLength);
		assertArrayEquals(expected, out.toByteArray());
	}

	@Test
	public void testWritePaddedFinalFrameWithTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, frameCipher,
				frameKey, tag);
		int payloadLength = 123, paddingLength = 234;
		byte[] payload = new byte[payloadLength];
		new Random().nextBytes(payload);

		s.writeFrame(payload, payloadLength, paddingLength, true);

		byte[] header = new byte[HEADER_LENGTH];
		FrameEncoder.encodeHeader(header, true, payloadLength, paddingLength);
		byte[] expected = new byte[TAG_LENGTH + HEADER_LENGTH + payloadLength
		                           + paddingLength + MAC_LENGTH];
		System.arraycopy(tag, 0, expected, 0, TAG_LENGTH);
		System.arraycopy(header, 0, expected, TAG_LENGTH, HEADER_LENGTH);
		System.arraycopy(payload, 0, expected, TAG_LENGTH + HEADER_LENGTH,
				payloadLength);
		assertArrayEquals(expected, out.toByteArray());
	}

	@Test
	public void testWritePaddedNonFinalFrameWithoutTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, frameCipher,
				frameKey, null);
		int payloadLength = 123, paddingLength = 234;
		byte[] payload = new byte[payloadLength];
		new Random().nextBytes(payload);

		s.writeFrame(payload, payloadLength, paddingLength, false);

		byte[] header = new byte[HEADER_LENGTH];
		FrameEncoder.encodeHeader(header, false, payloadLength, paddingLength);
		byte[] expected = new byte[HEADER_LENGTH + payloadLength
		                           + paddingLength + MAC_LENGTH];
		System.arraycopy(header, 0, expected, 0, HEADER_LENGTH);
		System.arraycopy(payload, 0, expected, HEADER_LENGTH, payloadLength);
		assertArrayEquals(expected, out.toByteArray());
	}

	@Test
	public void testWritePaddedFinalFrameWithoutTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, frameCipher,
				frameKey, null);
		int payloadLength = 123, paddingLength = 234;
		byte[] payload = new byte[payloadLength];
		new Random().nextBytes(payload);

		s.writeFrame(payload, payloadLength, paddingLength, true);

		byte[] header = new byte[HEADER_LENGTH];
		FrameEncoder.encodeHeader(header, true, payloadLength, paddingLength);
		byte[] expected = new byte[HEADER_LENGTH + payloadLength
		                           + paddingLength + MAC_LENGTH];
		System.arraycopy(header, 0, expected, 0, HEADER_LENGTH);
		System.arraycopy(payload, 0, expected, HEADER_LENGTH, payloadLength);
		assertArrayEquals(expected, out.toByteArray());
	}

	@Test
	public void testWriteTwoFrames() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, frameCipher,
				frameKey, null);
		int payloadLength = 123, paddingLength = 234;
		byte[] payload = new byte[payloadLength];
		new Random().nextBytes(payload);
		int payloadLength1 = 345, paddingLength1 = 456;
		byte[] payload1 = new byte[payloadLength1];
		new Random().nextBytes(payload1);

		s.writeFrame(payload, payloadLength, paddingLength, false);
		s.writeFrame(payload1, payloadLength1, paddingLength1, true);

		byte[] header = new byte[HEADER_LENGTH];
		FrameEncoder.encodeHeader(header, false, payloadLength, paddingLength);
		byte[] header1 = new byte[HEADER_LENGTH];
		FrameEncoder.encodeHeader(header1, true, payloadLength1,
				paddingLength1);
		byte[] expected = new byte[HEADER_LENGTH + payloadLength
		                           + paddingLength + MAC_LENGTH
		                           + HEADER_LENGTH + payloadLength1
		                           + paddingLength1 + MAC_LENGTH];
		System.arraycopy(header, 0, expected, 0, HEADER_LENGTH);
		System.arraycopy(payload, 0, expected, HEADER_LENGTH, payloadLength);
		System.arraycopy(header1, 0, expected, HEADER_LENGTH + payloadLength
				+ paddingLength + MAC_LENGTH, HEADER_LENGTH);
		System.arraycopy(payload1, 0, expected, HEADER_LENGTH + payloadLength
				+ paddingLength + MAC_LENGTH + HEADER_LENGTH, payloadLength1);
		assertArrayEquals(expected, out.toByteArray());
	}

	@Test
	public void testFlushWritesTagIfNotAlreadyWritten() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, frameCipher,
				frameKey, tag);
		s.flush();
		assertArrayEquals(tag, out.toByteArray());
	}

	@Test
	public void testFlushDoesNotWriteTagIfAlreadyWritten() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, frameCipher,
				frameKey, tag);
		s.flush();
		s.flush();
		assertArrayEquals(tag, out.toByteArray());
	}

	@Test
	public void testFlushDoesNotWriteTagIfNull() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl s = new StreamEncrypterImpl(out, frameCipher,
				frameKey, null);
		s.flush();
		assertEquals(0, out.size());
	}
}

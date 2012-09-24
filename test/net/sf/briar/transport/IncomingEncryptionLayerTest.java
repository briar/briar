package net.sf.briar.transport;

import static javax.crypto.Cipher.ENCRYPT_MODE;
import static net.sf.briar.api.transport.TransportConstants.AAD_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;

import java.io.ByteArrayInputStream;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.AuthenticatedCipher;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class IncomingEncryptionLayerTest extends BriarTestCase {

	// FIXME: This is an integration test, not a unit test

	private static final int FRAME_LENGTH = 1024;
	private static final int MAX_PAYLOAD_LENGTH =
			FRAME_LENGTH - HEADER_LENGTH - MAC_LENGTH;

	private final CryptoComponent crypto;
	private final AuthenticatedCipher frameCipher;
	private final ErasableKey frameKey;

	public IncomingEncryptionLayerTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		crypto = i.getInstance(CryptoComponent.class);
		frameCipher = crypto.getFrameCipher();
		frameKey = crypto.generateTestKey();
	}

	@Test
	public void testReadValidFrames() throws Exception {
		// Generate two valid frames
		byte[] frame = generateFrame(0L, FRAME_LENGTH, 123, false, false);
		byte[] frame1 = generateFrame(1L, FRAME_LENGTH, 123, false, false);
		// Concatenate the frames
		byte[] valid = new byte[FRAME_LENGTH * 2];
		System.arraycopy(frame, 0, valid, 0, FRAME_LENGTH);
		System.arraycopy(frame1, 0, valid, FRAME_LENGTH, FRAME_LENGTH);
		// Read the frames
		ByteArrayInputStream in = new ByteArrayInputStream(valid);
		IncomingEncryptionLayer i = new IncomingEncryptionLayer(in, frameCipher,
				frameKey, FRAME_LENGTH);
		byte[] buf = new byte[FRAME_LENGTH - MAC_LENGTH];
		assertEquals(123, i.readFrame(buf));
		assertEquals(123, i.readFrame(buf));
	}

	@Test
	public void testTruncatedFrameThrowsException() throws Exception {
		// Generate a valid frame
		byte[] frame = generateFrame(0L, FRAME_LENGTH, 123, false, false);
		// Chop off the last byte
		byte[] truncated = new byte[FRAME_LENGTH - 1];
		System.arraycopy(frame, 0, truncated, 0, FRAME_LENGTH - 1);
		// Try to read the frame, which should fail due to truncation
		ByteArrayInputStream in = new ByteArrayInputStream(truncated);
		IncomingEncryptionLayer i = new IncomingEncryptionLayer(in, frameCipher,
				frameKey, FRAME_LENGTH);
		try {
			i.readFrame(new byte[FRAME_LENGTH - MAC_LENGTH]);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testModifiedFrameThrowsException() throws Exception {
		// Generate a valid frame
		byte[] frame = generateFrame(0L, FRAME_LENGTH, 123, false, false);
		// Modify a randomly chosen byte of the frame
		frame[(int) (Math.random() * FRAME_LENGTH)] ^= 1;
		// Try to read the frame, which should fail due to modification
		ByteArrayInputStream in = new ByteArrayInputStream(frame);
		IncomingEncryptionLayer i = new IncomingEncryptionLayer(in, frameCipher,
				frameKey, FRAME_LENGTH);
		try {
			i.readFrame(new byte[FRAME_LENGTH - MAC_LENGTH]);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testShortNonFinalFrameThrowsException() throws Exception {
		// Generate a short non-final frame
		byte[] frame = generateFrame(0L, FRAME_LENGTH - 1, 123, false, false);
		// Try to read the frame, which should fail due to invalid length
		ByteArrayInputStream in = new ByteArrayInputStream(frame);
		IncomingEncryptionLayer i = new IncomingEncryptionLayer(in, frameCipher,
				frameKey, FRAME_LENGTH);
		try {
			i.readFrame(new byte[FRAME_LENGTH - MAC_LENGTH]);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testShortFinalFrameDoesNotThrowException() throws Exception {
		// Generate a short final frame
		byte[] frame = generateFrame(0L, FRAME_LENGTH - 1, 123, true, false);
		// Read the frame
		ByteArrayInputStream in = new ByteArrayInputStream(frame);
		IncomingEncryptionLayer i = new IncomingEncryptionLayer(in, frameCipher,
				frameKey, FRAME_LENGTH);
		int length = i.readFrame(new byte[FRAME_LENGTH - MAC_LENGTH]);
		assertEquals(123, length);
	}

	@Test
	public void testInvalidPayloadLengthThrowsException() throws Exception {
		// Generate a frame with an invalid payload length
		byte[] frame = generateFrame(0L, FRAME_LENGTH, MAX_PAYLOAD_LENGTH + 1,
				false, false);
		// Try to read the frame, which should fail due to invalid length
		ByteArrayInputStream in = new ByteArrayInputStream(frame);
		IncomingEncryptionLayer i = new IncomingEncryptionLayer(in, frameCipher,
				frameKey, FRAME_LENGTH);
		try {
			i.readFrame(new byte[FRAME_LENGTH - MAC_LENGTH]);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testNonZeroPaddingThrowsException() throws Exception {
		// Generate a frame with bad padding
		byte[] frame = generateFrame(0L, FRAME_LENGTH, 123, false, true);
		// Try to read the frame, which should fail due to bad padding
		ByteArrayInputStream in = new ByteArrayInputStream(frame);
		IncomingEncryptionLayer i = new IncomingEncryptionLayer(in, frameCipher,
				frameKey, FRAME_LENGTH);
		try {
			i.readFrame(new byte[FRAME_LENGTH - MAC_LENGTH]);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testCannotReadBeyondFinalFrame() throws Exception {
		// Generate a valid final frame and another valid final frame after it
		byte[] frame = generateFrame(0L, FRAME_LENGTH, MAX_PAYLOAD_LENGTH, true,
				false);
		byte[] frame1 = generateFrame(1L, FRAME_LENGTH, 123, true, false);
		// Concatenate the frames
		byte[] extraFrame = new byte[FRAME_LENGTH * 2];
		System.arraycopy(frame, 0, extraFrame, 0, FRAME_LENGTH);
		System.arraycopy(frame1, 0, extraFrame, FRAME_LENGTH, FRAME_LENGTH);
		// Read the final frame, which should first read the tag
		ByteArrayInputStream in = new ByteArrayInputStream(extraFrame);
		IncomingEncryptionLayer i = new IncomingEncryptionLayer(in, frameCipher,
				frameKey, FRAME_LENGTH);
		byte[] buf = new byte[FRAME_LENGTH - MAC_LENGTH];
		assertEquals(MAX_PAYLOAD_LENGTH, i.readFrame(buf));
		// The frame after the final frame should not be read
		assertEquals(-1, i.readFrame(buf));
	}

	private byte[] generateFrame(long frameNumber, int frameLength,
			int payloadLength, boolean finalFrame, boolean badPadding)
					throws Exception {
		byte[] iv = new byte[IV_LENGTH], aad = new byte[AAD_LENGTH];
		byte[] plaintext = new byte[frameLength - MAC_LENGTH];
		byte[] ciphertext = new byte[frameLength];
		FrameEncoder.encodeIv(iv, frameNumber);
		FrameEncoder.encodeAad(aad, frameNumber, plaintext.length);
		frameCipher.init(ENCRYPT_MODE, frameKey, iv, aad);
		FrameEncoder.encodeHeader(plaintext, finalFrame, payloadLength);
		if(badPadding) plaintext[HEADER_LENGTH + payloadLength] = 1;
		frameCipher.doFinal(plaintext, 0, plaintext.length, ciphertext, 0);
		return ciphertext;
	}
}

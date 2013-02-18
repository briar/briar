package net.sf.briar.transport;

import static javax.crypto.Cipher.ENCRYPT_MODE;
import static net.sf.briar.api.transport.TransportConstants.AAD_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.ByteArrayOutputStream;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.AuthenticatedCipher;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class OutgoingEncryptionLayerTest extends BriarTestCase {

	// FIXME: This is an integration test, not a unit test

	private static final int FRAME_LENGTH = 1024;
	private static final int MAX_PAYLOAD_LENGTH =
			FRAME_LENGTH - HEADER_LENGTH - MAC_LENGTH;

	private final CryptoComponent crypto;
	private final AuthenticatedCipher frameCipher;
	private final byte[] tag;

	public OutgoingEncryptionLayerTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		crypto = i.getInstance(CryptoComponent.class);
		frameCipher = crypto.getFrameCipher();
		tag = new byte[TAG_LENGTH];
	}

	@Test
	public void testEncryption() throws Exception {
		int payloadLength = 123;
		byte[] iv = new byte[IV_LENGTH], aad = new byte[AAD_LENGTH];
		byte[] plaintext = new byte[FRAME_LENGTH - MAC_LENGTH];
		byte[] ciphertext = new byte[FRAME_LENGTH];
		ErasableKey frameKey = crypto.generateSecretKey();
		// Calculate the expected ciphertext
		FrameEncoder.encodeIv(iv, 0);
		FrameEncoder.encodeAad(aad, 0, plaintext.length);
		frameCipher.init(ENCRYPT_MODE, frameKey, iv, aad);
		FrameEncoder.encodeHeader(plaintext, false, payloadLength);
		frameCipher.doFinal(plaintext, 0, plaintext.length, ciphertext, 0);
		// Check that the actual tag and ciphertext match what's expected
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		OutgoingEncryptionLayer o = new OutgoingEncryptionLayer(out,
				10 * FRAME_LENGTH, frameCipher, frameKey, FRAME_LENGTH, tag);
		o.writeFrame(new byte[FRAME_LENGTH - MAC_LENGTH], payloadLength, false);
		byte[] actual = out.toByteArray();
		assertEquals(TAG_LENGTH + FRAME_LENGTH, actual.length);
		for(int i = 0; i < TAG_LENGTH; i++) assertEquals(tag[i], actual[i]);
		for(int i = 0; i < FRAME_LENGTH; i++) {
			assertEquals("" + i, ciphertext[i], actual[TAG_LENGTH + i]);
		}
	}

	@Test
	public void testInitiatorClosesConnectionWithoutWriting() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// Initiator's constructor
		OutgoingEncryptionLayer o = new OutgoingEncryptionLayer(out,
				10 * FRAME_LENGTH, frameCipher, crypto.generateSecretKey(),
				FRAME_LENGTH, tag);
		// Write an empty final frame without having written any other frames
		o.writeFrame(new byte[FRAME_LENGTH - MAC_LENGTH], 0, true);
		// Nothing should be written to the output stream
		assertEquals(0, out.size());
	}

	@Test
	public void testResponderClosesConnectionWithoutWriting() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// Responder's constructor
		OutgoingEncryptionLayer o = new OutgoingEncryptionLayer(out,
				10 * FRAME_LENGTH, frameCipher, crypto.generateSecretKey(),
				FRAME_LENGTH);
		// Write an empty final frame without having written any other frames
		o.writeFrame(new byte[FRAME_LENGTH - MAC_LENGTH], 0, true);
		// An empty final frame should be written to the output stream
		assertEquals(HEADER_LENGTH + MAC_LENGTH, out.size());
	}

	@Test
	public void testRemainingCapacityWithTag() throws Exception {
		int MAX_PAYLOAD_LENGTH = FRAME_LENGTH - HEADER_LENGTH - MAC_LENGTH;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// Initiator's constructor
		OutgoingEncryptionLayer o = new OutgoingEncryptionLayer(out,
				10 * FRAME_LENGTH, frameCipher, crypto.generateSecretKey(),
				FRAME_LENGTH, tag);
		// There should be space for nine full frames and one partial frame
		byte[] frame = new byte[FRAME_LENGTH - MAC_LENGTH];
		assertEquals(10 * MAX_PAYLOAD_LENGTH - TAG_LENGTH,
				o.getRemainingCapacity());
		// Write nine frames, each containing a partial payload
		for(int i = 0; i < 9; i++) {
			o.writeFrame(frame, 123, false);
			assertEquals((9 - i) * MAX_PAYLOAD_LENGTH - TAG_LENGTH,
					o.getRemainingCapacity());
		}
		// Write the final frame, which will not be padded
		o.writeFrame(frame, 123, true);
		int finalFrameLength = HEADER_LENGTH + 123 + MAC_LENGTH;
		assertEquals(MAX_PAYLOAD_LENGTH - TAG_LENGTH - finalFrameLength,
				o.getRemainingCapacity());
	}

	@Test
	public void testRemainingCapacityWithoutTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// Responder's constructor
		OutgoingEncryptionLayer o = new OutgoingEncryptionLayer(out,
				10 * FRAME_LENGTH, frameCipher, crypto.generateSecretKey(),
				FRAME_LENGTH);
		// There should be space for ten full frames
		assertEquals(10 * MAX_PAYLOAD_LENGTH, o.getRemainingCapacity());
		// Write nine frames, each containing a partial payload
		byte[] frame = new byte[FRAME_LENGTH - MAC_LENGTH];
		for(int i = 0; i < 9; i++) {
			o.writeFrame(frame, 123, false);
			assertEquals((9 - i) * MAX_PAYLOAD_LENGTH,
					o.getRemainingCapacity());
		}
		// Write the final frame, which will not be padded
		o.writeFrame(frame, 123, true);
		int finalFrameLength = HEADER_LENGTH + 123 + MAC_LENGTH;
		assertEquals(MAX_PAYLOAD_LENGTH - finalFrameLength,
				o.getRemainingCapacity());
	}

	@Test
	public void testRemainingCapacityLimitedByFrameNumbers() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// The connection has plenty of space so we're limited by frame numbers
		OutgoingEncryptionLayer o = new OutgoingEncryptionLayer(out,
				Long.MAX_VALUE, frameCipher, crypto.generateSecretKey(),
				FRAME_LENGTH);
		// There should be enough frame numbers for 2^32 frames
		assertEquals((1L << 32) * MAX_PAYLOAD_LENGTH, o.getRemainingCapacity());
		// Write a frame containing a partial payload
		byte[] frame = new byte[FRAME_LENGTH - MAC_LENGTH];
		o.writeFrame(frame, 123, false);
		// There should be enough frame numbers for 2^32 - 1 frames
		assertEquals(((1L << 32) - 1) * MAX_PAYLOAD_LENGTH,
				o.getRemainingCapacity());
	}
}

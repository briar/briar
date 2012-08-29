package net.sf.briar.transport;

import static javax.crypto.Cipher.ENCRYPT_MODE;
import static net.sf.briar.api.transport.TransportConstants.AAD_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.ByteArrayOutputStream;

import javax.crypto.Cipher;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.AuthenticatedCipher;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class OutgoingEncryptionLayerTest extends BriarTestCase {

	private final CryptoComponent crypto;
	private final Cipher tagCipher;
	private final AuthenticatedCipher frameCipher;

	public OutgoingEncryptionLayerTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		crypto = i.getInstance(CryptoComponent.class);
		tagCipher = crypto.getTagCipher();
		frameCipher = crypto.getFrameCipher();
	}

	@Test
	public void testEncryption() throws Exception {
		int frameLength = 1024, payloadLength = 123;
		byte[] tag = new byte[TAG_LENGTH];
		byte[] iv = new byte[IV_LENGTH], aad = new byte[AAD_LENGTH];
		byte[] plaintext = new byte[frameLength - MAC_LENGTH];
		byte[] ciphertext = new byte[frameLength];
		ErasableKey tagKey = crypto.generateTestKey();
		ErasableKey frameKey = crypto.generateTestKey();
		// Calculate the expected tag
		TagEncoder.encodeTag(tag, tagCipher, tagKey);
		// Calculate the expected ciphertext
		FrameEncoder.encodeIv(iv, 0);
		FrameEncoder.encodeAad(aad, 0, plaintext.length);
		frameCipher.init(ENCRYPT_MODE, frameKey, iv, aad);
		FrameEncoder.encodeHeader(plaintext, false, payloadLength);
		frameCipher.doFinal(plaintext, 0, plaintext.length, ciphertext, 0);
		// Check that the actual tag and ciphertext match what's expected
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		OutgoingEncryptionLayer o = new OutgoingEncryptionLayer(out, 10 * 1024,
				tagCipher, frameCipher, tagKey, frameKey, frameLength);
		o.writeFrame(new byte[frameLength - MAC_LENGTH], payloadLength, false);
		byte[] actual = out.toByteArray();
		assertEquals(TAG_LENGTH + frameLength, actual.length);
		for(int i = 0; i < TAG_LENGTH; i++) {
			assertEquals(tag[i], actual[i]);
		}
		for(int i = 0; i < frameLength; i++) {
			assertEquals("" + i, ciphertext[i], actual[TAG_LENGTH + i]);
		}
	}

	@Test
	public void testInitiatorClosesConnectionWithoutWriting() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// Initiator's constructor
		OutgoingEncryptionLayer o = new OutgoingEncryptionLayer(out, 10 * 1024,
				tagCipher, frameCipher, crypto.generateTestKey(),
				crypto.generateTestKey(), 1024);
		// Write an empty final frame without having written any other frames
		o.writeFrame(new byte[1024], 0, true);
		// Nothing should be written to the output stream
		assertEquals(0, out.size());
	}

	@Test
	public void testResponderClosesConnectionWithoutWriting() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// Responder's constructor
		OutgoingEncryptionLayer o = new OutgoingEncryptionLayer(out, 10 * 1024,
				frameCipher, crypto.generateTestKey(), 1024);
		// Write an empty final frame without having written any other frames
		o.writeFrame(new byte[1024], 0, true);
		// An empty final frame should be written to the output stream
		assertEquals(HEADER_LENGTH + MAC_LENGTH, out.size());
	}

	@Test
	public void testRemainingCapacityWithTag() throws Exception {
		int frameLength = 1024;
		int maxPayloadLength = frameLength - HEADER_LENGTH - MAC_LENGTH;
		long capacity = 10 * frameLength;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// Initiator's constructor
		OutgoingEncryptionLayer o = new OutgoingEncryptionLayer(out, capacity,
				tagCipher, frameCipher, crypto.generateTestKey(),
				crypto.generateTestKey(), frameLength);
		// There should be space for nine full frames and one partial frame
		byte[] frame = new byte[frameLength];
		assertEquals(10 * maxPayloadLength - TAG_LENGTH,
				o.getRemainingCapacity());
		// Write nine frames, each containing a partial payload
		for(int i = 0; i < 9; i++) {
			o.writeFrame(frame, 123, false);
			assertEquals((9 - i) * maxPayloadLength - TAG_LENGTH,
					o.getRemainingCapacity());
		}
		// Write the final frame, which will not be padded
		o.writeFrame(frame, 123, true);
		int finalFrameLength = HEADER_LENGTH + 123 + MAC_LENGTH;
		assertEquals(maxPayloadLength - TAG_LENGTH - finalFrameLength,
				o.getRemainingCapacity());
	}

	@Test
	public void testRemainingCapacityWithoutTag() throws Exception {
		int frameLength = 1024;
		int maxPayloadLength = frameLength - HEADER_LENGTH - MAC_LENGTH;
		long capacity = 10 * frameLength;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// Responder's constructor
		OutgoingEncryptionLayer o = new OutgoingEncryptionLayer(out, capacity,
				frameCipher, crypto.generateTestKey(), frameLength);
		// There should be space for ten full frames
		assertEquals(10 * maxPayloadLength, o.getRemainingCapacity());
		// Write nine frames, each containing a partial payload
		byte[] frame = new byte[frameLength];
		for(int i = 0; i < 9; i++) {
			o.writeFrame(frame, 123, false);
			assertEquals((9 - i) * maxPayloadLength, o.getRemainingCapacity());
		}
		// Write the final frame, which will not be padded
		o.writeFrame(frame, 123, true);
		int finalFrameLength = HEADER_LENGTH + 123 + MAC_LENGTH;
		assertEquals(maxPayloadLength - finalFrameLength,
				o.getRemainingCapacity());
	}

	@Test
	public void testRemainingCapacityLimitedByFrameNumbers() throws Exception {
		int frameLength = 1024;
		int maxPayloadLength = frameLength - HEADER_LENGTH - MAC_LENGTH;
		long capacity = Long.MAX_VALUE;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		OutgoingEncryptionLayer o = new OutgoingEncryptionLayer(out, capacity,
				frameCipher, crypto.generateTestKey(), frameLength);
		// There should be enough frame numbers for 2^32 frames
		assertEquals((1L << 32) * maxPayloadLength, o.getRemainingCapacity());
		// Write a frame containing a partial payload
		byte[] frame = new byte[frameLength];
		o.writeFrame(frame, 123, false);
		// There should be enough frame numbers for 2^32 - 1 frames
		assertEquals(((1L << 32) - 1) * maxPayloadLength,
				o.getRemainingCapacity());
	}
}

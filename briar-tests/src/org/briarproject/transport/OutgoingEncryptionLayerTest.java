package org.briarproject.transport;

import static org.briarproject.api.transport.TransportConstants.AAD_LENGTH;
import static org.briarproject.api.transport.TransportConstants.HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.IV_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;

import java.io.ByteArrayOutputStream;

import org.briarproject.BriarTestCase;
import org.briarproject.TestLifecycleModule;
import org.briarproject.TestSystemModule;
import org.briarproject.api.crypto.AuthenticatedCipher;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.crypto.CryptoModule;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class OutgoingEncryptionLayerTest extends BriarTestCase {

	// FIXME: This is an integration test, not a unit test

	private static final int FRAME_LENGTH = 1024;

	private final CryptoComponent crypto;
	private final AuthenticatedCipher frameCipher;
	private final byte[] tag;

	public OutgoingEncryptionLayerTest() {
		Injector i = Guice.createInjector(new CryptoModule(),
				new TestLifecycleModule(), new TestSystemModule());
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
		SecretKey frameKey = crypto.generateSecretKey();
		// Calculate the expected ciphertext
		FrameEncoder.encodeIv(iv, 0);
		FrameEncoder.encodeAad(aad, 0, plaintext.length);
		frameCipher.init(true, frameKey, iv, aad);
		FrameEncoder.encodeHeader(plaintext, false, payloadLength);
		frameCipher.doFinal(plaintext, 0, plaintext.length, ciphertext, 0);
		// Check that the actual tag and ciphertext match what's expected
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		OutgoingEncryptionLayer o = new OutgoingEncryptionLayer(out,
				frameCipher, frameKey, FRAME_LENGTH, tag);
		o.writeFrame(new byte[FRAME_LENGTH - MAC_LENGTH], payloadLength, false);
		byte[] actual = out.toByteArray();
		assertEquals(TAG_LENGTH + FRAME_LENGTH, actual.length);
		for(int i = 0; i < TAG_LENGTH; i++) assertEquals(tag[i], actual[i]);
		for(int i = 0; i < FRAME_LENGTH; i++) {
			assertEquals("" + i, ciphertext[i], actual[TAG_LENGTH + i]);
		}
	}

	@Test
	public void testCloseConnectionWithoutWriting() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// Initiator's constructor
		OutgoingEncryptionLayer o = new OutgoingEncryptionLayer(out,
				frameCipher, crypto.generateSecretKey(), FRAME_LENGTH, tag);
		// Write an empty final frame without having written any other frames
		o.writeFrame(new byte[FRAME_LENGTH - MAC_LENGTH], 0, true);
		// The tag and the empty frame should be written to the output stream
		assertEquals(TAG_LENGTH + HEADER_LENGTH + MAC_LENGTH, out.size());
	}
}

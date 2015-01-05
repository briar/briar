package org.briarproject.crypto;

import static org.briarproject.api.transport.TransportConstants.AAD_LENGTH;
import static org.briarproject.api.transport.TransportConstants.HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.IV_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;

import java.io.ByteArrayOutputStream;
import java.util.Random;

import org.briarproject.BriarTestCase;
import org.briarproject.TestLifecycleModule;
import org.briarproject.TestSystemModule;
import org.briarproject.api.crypto.AuthenticatedCipher;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class StreamEncrypterImplTest extends BriarTestCase {

	// FIXME: This is an integration test, not a unit test

	private final CryptoComponent crypto;
	private final AuthenticatedCipher frameCipher;

	public StreamEncrypterImplTest() {
		Injector i = Guice.createInjector(new CryptoModule(),
				new TestLifecycleModule(), new TestSystemModule());
		crypto = i.getInstance(CryptoComponent.class);
		frameCipher = crypto.getFrameCipher();
	}

	@Test
	public void testEncryptionWithoutTag() throws Exception {
		int payloadLength = 123;
		byte[] iv = new byte[IV_LENGTH], aad = new byte[AAD_LENGTH];
		byte[] plaintext = new byte[MAX_FRAME_LENGTH - MAC_LENGTH];
		byte[] ciphertext = new byte[MAX_FRAME_LENGTH];
		SecretKey frameKey = crypto.generateSecretKey();
		// Calculate the expected ciphertext
		FrameEncoder.encodeIv(iv, 0);
		FrameEncoder.encodeAad(aad, 0, plaintext.length);
		frameCipher.init(true, frameKey, iv, aad);
		FrameEncoder.encodeHeader(plaintext, false, payloadLength);
		frameCipher.doFinal(plaintext, 0, plaintext.length, ciphertext, 0);
		// Check that the actual ciphertext matches what's expected
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl o = new StreamEncrypterImpl(out, frameCipher,
				frameKey, null);
		o.writeFrame(new byte[payloadLength], payloadLength, false);
		byte[] actual = out.toByteArray();
		assertEquals(MAX_FRAME_LENGTH, actual.length);
		for(int i = 0; i < MAX_FRAME_LENGTH; i++)
			assertEquals(ciphertext[i], actual[i]);
	}

	@Test
	public void testEncryptionWithTag() throws Exception {
		byte[] tag = new byte[TAG_LENGTH];
		new Random().nextBytes(tag);
		int payloadLength = 123;
		byte[] iv = new byte[IV_LENGTH], aad = new byte[AAD_LENGTH];
		byte[] plaintext = new byte[MAX_FRAME_LENGTH - MAC_LENGTH];
		byte[] ciphertext = new byte[MAX_FRAME_LENGTH];
		SecretKey frameKey = crypto.generateSecretKey();
		// Calculate the expected ciphertext
		FrameEncoder.encodeIv(iv, 0);
		FrameEncoder.encodeAad(aad, 0, plaintext.length);
		frameCipher.init(true, frameKey, iv, aad);
		FrameEncoder.encodeHeader(plaintext, false, payloadLength);
		frameCipher.doFinal(plaintext, 0, plaintext.length, ciphertext, 0);
		// Check that the actual tag and ciphertext match what's expected
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamEncrypterImpl o = new StreamEncrypterImpl(out, frameCipher,
				frameKey, tag);
		o.writeFrame(new byte[payloadLength], payloadLength, false);
		byte[] actual = out.toByteArray();
		assertEquals(TAG_LENGTH + MAX_FRAME_LENGTH, actual.length);
		for(int i = 0; i < TAG_LENGTH; i++) assertEquals(tag[i], actual[i]);
		for(int i = 0; i < MAX_FRAME_LENGTH; i++)
			assertEquals(ciphertext[i], actual[TAG_LENGTH + i]);
	}

	@Test
	public void testCloseConnectionWithoutWriting() throws Exception {
		byte[] tag = new byte[TAG_LENGTH];
		new Random().nextBytes(tag);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// Initiator's constructor
		StreamEncrypterImpl o = new StreamEncrypterImpl(out, frameCipher,
				crypto.generateSecretKey(), tag);
		// Write an empty final frame without having written any other frames
		o.writeFrame(new byte[MAX_FRAME_LENGTH - MAC_LENGTH], 0, true);
		// The tag and the empty frame should be written to the output stream
		assertEquals(TAG_LENGTH + HEADER_LENGTH + MAC_LENGTH, out.size());
	}
}

package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.ByteArrayInputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.crypto.CryptoModule;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConnectionDecrypterTest extends BriarTestCase {

	private static final int MAC_LENGTH = 32;

	private final Cipher tagCipher, frameCipher;
	private final ErasableKey tagKey, frameKey;

	public ConnectionDecrypterTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		tagCipher = crypto.getTagCipher();
		frameCipher = crypto.getFrameCipher();
		tagKey = crypto.generateTestKey();
		frameKey = crypto.generateTestKey();
	}

	@Test
	public void testDecryptionWithFirstSegmentTagged() throws Exception {
		// Calculate the ciphertext for the first frame
		byte[] plaintext = new byte[FRAME_HEADER_LENGTH + 123 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext, 0L, 123, 0);
		byte[] iv = IvEncoder.encodeIv(0L, frameCipher.getBlockSize());
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext = frameCipher.doFinal(plaintext, 0, plaintext.length);
		// Calculate the ciphertext for the second frame
		byte[] plaintext1 = new byte[FRAME_HEADER_LENGTH + 1234 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext1, 1L, 1234, 0);
		IvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext1 = frameCipher.doFinal(plaintext1, 0,
				plaintext1.length);
		// Concatenate the ciphertexts
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(ciphertext);
		out.write(ciphertext1);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		// Use a ConnectionDecrypter to decrypt the ciphertext
		FrameSource decrypter = new ConnectionDecrypter(in, tagCipher,
				frameCipher, tagKey, frameKey, MAC_LENGTH, false);
		// First frame
		byte[] decrypted = new byte[MAX_FRAME_LENGTH];
		assertEquals(plaintext.length, decrypter.readFrame(decrypted));
		for(int i = 0; i < plaintext.length; i++) {
			assertEquals(plaintext[i], decrypted[i]);
		}
		// Second frame
		assertEquals(plaintext1.length, decrypter.readFrame(decrypted));
		for(int i = 0; i < plaintext1.length; i++) {
			assertEquals(plaintext1[i], decrypted[i]);
		}
	}

	@Test
	public void testDecryptionWithEverySegmentTagged() throws Exception {
		// Calculate the ciphertext for the first frame
		byte[] plaintext = new byte[FRAME_HEADER_LENGTH + 123 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext, 0L, 123, 0);
		byte[] iv = IvEncoder.encodeIv(0L, frameCipher.getBlockSize());
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext = frameCipher.doFinal(plaintext, 0, plaintext.length);
		// Calculate the ciphertext for the second frame, including its tag
		byte[] plaintext1 = new byte[FRAME_HEADER_LENGTH + 1234 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext1, 1L, 1234, 0);
		byte[] ciphertext1 = new byte[TAG_LENGTH + plaintext1.length];
		TagEncoder.encodeTag(ciphertext1, 1, tagCipher, tagKey);
		IvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		frameCipher.doFinal(plaintext1, 0, plaintext1.length, ciphertext1,
				TAG_LENGTH);
		// Concatenate the ciphertexts
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(ciphertext);
		out.write(ciphertext1);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		// Use a ConnectionDecrypter to decrypt the ciphertext
		FrameSource decrypter = new ConnectionDecrypter(in, tagCipher,
				frameCipher, tagKey, frameKey, MAC_LENGTH, true);
		// First frame
		byte[] decrypted = new byte[MAX_FRAME_LENGTH];
		assertEquals(plaintext.length, decrypter.readFrame(decrypted));
		for(int i = 0; i < plaintext.length; i++) {
			assertEquals(plaintext[i], decrypted[i]);
		}
		// Second frame
		assertEquals(plaintext1.length, decrypter.readFrame(decrypted));
		for(int i = 0; i < plaintext1.length; i++) {
			assertEquals(plaintext1[i], decrypted[i]);
		}
	}
}

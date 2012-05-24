package net.sf.briar.crypto;

import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.crypto.IvEncoder;
import net.sf.briar.util.ByteUtils;

import org.junit.Test;

public class FramePeekingTest extends BriarTestCase {

	@Test
	public void testFramePeeking() throws Exception {
		CryptoComponent crypto = new CryptoComponentImpl();
		ErasableKey key = crypto.generateTestKey();

		Cipher frameCipher = crypto.getFrameCipher();
		IvEncoder frameIvEncoder = crypto.getFrameIvEncoder();
		byte[] iv = frameIvEncoder.encodeIv(ByteUtils.MAX_32_BIT_UNSIGNED);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);

		Cipher framePeekingCipher = crypto.getFramePeekingCipher();
		IvEncoder framePeekingIvEncoder = crypto.getFramePeekingIvEncoder();
		iv = framePeekingIvEncoder.encodeIv(ByteUtils.MAX_32_BIT_UNSIGNED);
		ivSpec = new IvParameterSpec(iv);
		framePeekingCipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);

		// The ciphers should produce the same ciphertext, apart from the MAC
		byte[] plaintext = new byte[123];
		byte[] ciphertext = frameCipher.doFinal(plaintext);
		byte[] peekingCiphertext = framePeekingCipher.doFinal(plaintext);
		assertEquals(ciphertext.length, peekingCiphertext.length + MAC_LENGTH);
		for(int i = 0; i < peekingCiphertext.length; i++) {
			assertEquals(ciphertext[i], peekingCiphertext[i]);
		}
	}
}

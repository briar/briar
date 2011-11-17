package net.sf.briar.crypto;

import static org.junit.Assert.assertArrayEquals;

import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;

import junit.framework.TestCase;
import net.sf.briar.api.crypto.ErasableKey;

import org.junit.Test;

public class ErasableKeyTest extends TestCase {

	private static final String CIPHER = "AES";
	private static final String CIPHER_MODE = "AES/CTR/NoPadding";
	private static final int IV_BYTES = 16; // 128 bits
	private static final int KEY_BYTES = 32; // 256 bits
	private static final String MAC = "HMacSHA256";

	private final Random random = new Random();

	@Test
	public void testCopiesAreErased() {
		byte[] master = new byte[KEY_BYTES];
		random.nextBytes(master);
		ErasableKey k = new ErasableKeyImpl(master, CIPHER);
		byte[] copy = k.getEncoded();
		assertArrayEquals(master, copy);
		k.erase();
		byte[] blank = new byte[KEY_BYTES];
		assertArrayEquals(blank, master);
		assertArrayEquals(blank, copy);
	}

	@Test
	public void testErasureDoesNotAffectCipher() throws Exception {
		byte[] key = new byte[KEY_BYTES];
		random.nextBytes(key);
		ErasableKey k = new ErasableKeyImpl(key, CIPHER);
		Cipher c = Cipher.getInstance(CIPHER_MODE);
		IvParameterSpec iv = new IvParameterSpec(new byte[IV_BYTES]);
		c.init(Cipher.ENCRYPT_MODE, k, iv);
		// Encrypt a blank plaintext
		byte[] plaintext = new byte[123];
		byte[] ciphertext = c.doFinal(plaintext);
		// Erase the key and encrypt again - erase() was called after doFinal()
		k.erase();
		byte[] ciphertext1 = c.doFinal(plaintext);
		// Encrypt again - this time erase() was called before doFinal()
		byte[] ciphertext2 = c.doFinal(plaintext);
		// The ciphertexts should match
		assertArrayEquals(ciphertext, ciphertext1);
		assertArrayEquals(ciphertext, ciphertext2);
	}

	@Test
	public void testErasureDoesNotAffectMac() throws Exception {
		byte[] key = new byte[KEY_BYTES];
		random.nextBytes(key);
		ErasableKey k = new ErasableKeyImpl(key, CIPHER);
		Mac m = Mac.getInstance(MAC);
		m.init(k);
		// Authenticate a blank plaintext
		byte[] plaintext = new byte[123];
		byte[] mac = m.doFinal(plaintext);
		// Erase the key and authenticate again
		k.erase();
		byte[] mac1 = m.doFinal(plaintext);
		// Authenticate again
		byte[] mac2 = m.doFinal(plaintext);
		// The MACs should match
		assertArrayEquals(mac, mac1);
		assertArrayEquals(mac, mac2);
	}
}

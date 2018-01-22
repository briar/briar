package org.briarproject.bramble.crypto;

import org.briarproject.bramble.system.SystemClock;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestSecureRandomProvider;
import org.briarproject.bramble.test.TestUtils;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

public class PasswordBasedEncryptionTest extends BrambleTestCase {

	private final CryptoComponentImpl crypto =
			new CryptoComponentImpl(new TestSecureRandomProvider(),
					new ScryptKdf(new SystemClock()));

	@Test
	public void testEncryptionAndDecryption() {
		byte[] input = TestUtils.getRandomBytes(1234);
		String password = "password";
		byte[] ciphertext = crypto.encryptWithPassword(input, password);
		byte[] output = crypto.decryptWithPassword(ciphertext, password);
		assertArrayEquals(input, output);
	}

	@Test
	public void testInvalidCiphertextReturnsNull() {
		byte[] input = TestUtils.getRandomBytes(1234);
		String password = "password";
		byte[] ciphertext = crypto.encryptWithPassword(input, password);
		// Modify the ciphertext
		int position = new Random().nextInt(ciphertext.length);
		ciphertext[position] = (byte) (ciphertext[position] ^ 0xFF);
		byte[] output = crypto.decryptWithPassword(ciphertext, password);
		assertNull(output);
	}
}

package org.briarproject.bramble.crypto;

import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestSecureRandomProvider;
import org.briarproject.bramble.test.TestUtils;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PasswordBasedKdfTest extends BrambleTestCase {

	private final CryptoComponentImpl crypto =
			new CryptoComponentImpl(new TestSecureRandomProvider());

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

	@Test
	public void testCalibration() {
		// If the target time is unachievable, one iteration should be used
		int iterations = crypto.chooseIterationCount(0);
		assertEquals(1, iterations);
		// If the target time is long, more than one iteration should be used
		iterations = crypto.chooseIterationCount(10 * 1000);
		assertTrue(iterations > 1);
		// If the target time is very long, max iterations should be used
		iterations = crypto.chooseIterationCount(Integer.MAX_VALUE);
		assertEquals(Integer.MAX_VALUE, iterations);
	}
}

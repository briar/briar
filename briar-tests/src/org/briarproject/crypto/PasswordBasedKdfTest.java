package org.briarproject.crypto;

import static org.junit.Assert.assertArrayEquals;

import java.util.Random;

import org.briarproject.BriarTestCase;
import org.briarproject.TestSeedProvider;
import org.junit.Test;

public class PasswordBasedKdfTest extends BriarTestCase {

	private final CryptoComponentImpl crypto =
			new CryptoComponentImpl(new TestSeedProvider());

	@Test
	public void testEncryptionAndDecryption() {
		Random random = new Random();
		byte[] input = new byte[1234];
		random.nextBytes(input);
		char[] password = "password".toCharArray();
		byte[] ciphertext = crypto.encryptWithPassword(input, password);
		byte[] output = crypto.decryptWithPassword(ciphertext, password);
		assertArrayEquals(input, output);
	}

	@Test
	public void testInvalidCiphertextReturnsNull() {
		Random random = new Random();
		byte[] input = new byte[1234];
		random.nextBytes(input);
		char[] password = "password".toCharArray();
		byte[] ciphertext = crypto.encryptWithPassword(input, password);
		// Modify the ciphertext
		int position = random.nextInt(ciphertext.length);
		int value = random.nextInt(256);
		ciphertext[position] = (byte) value;
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

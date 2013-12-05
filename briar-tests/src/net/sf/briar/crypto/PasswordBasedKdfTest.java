package net.sf.briar.crypto;

import static org.junit.Assert.assertArrayEquals;

import java.util.Random;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;

import org.junit.Test;

public class PasswordBasedKdfTest extends BriarTestCase {

	@Test
	public void testEncryptionAndDecryption() {
		CryptoComponent crypto = new CryptoComponentImpl();
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
		CryptoComponent crypto = new CryptoComponentImpl();
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
		CryptoComponentImpl crypto = new CryptoComponentImpl();
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

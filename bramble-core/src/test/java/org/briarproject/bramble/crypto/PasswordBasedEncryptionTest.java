package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.DecryptionException;
import org.briarproject.bramble.api.crypto.KeyStrengthener;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.system.SystemClock;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.TestSecureRandomProvider;
import org.jmock.Expectations;
import org.junit.Test;

import static org.briarproject.bramble.api.crypto.DecryptionResult.INVALID_CIPHERTEXT;
import static org.briarproject.bramble.api.crypto.DecryptionResult.INVALID_PASSWORD;
import static org.briarproject.bramble.api.crypto.DecryptionResult.KEY_STRENGTHENER_ERROR;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PasswordBasedEncryptionTest extends BrambleMockTestCase {

	private final KeyStrengthener keyStrengthener =
			context.mock(KeyStrengthener.class);

	private final CryptoComponentImpl crypto =
			new CryptoComponentImpl(new TestSecureRandomProvider(),
					new ScryptKdf(new SystemClock()));

	@Test
	public void testEncryptionAndDecryption() throws Exception {
		byte[] input = getRandomBytes(1234);
		String password = "password";
		byte[] ciphertext = crypto.encryptWithPassword(input, password, null);
		byte[] output = crypto.decryptWithPassword(ciphertext, password, null);
		assertArrayEquals(input, output);
	}

	@Test
	public void testInvalidFormatVersionThrowsException() {
		byte[] input = getRandomBytes(1234);
		String password = "password";
		byte[] ciphertext = crypto.encryptWithPassword(input, password, null);

		// Modify the format version
		ciphertext[0] ^= (byte) 0xFF;
		try {
			crypto.decryptWithPassword(ciphertext, password, null);
			fail();
		} catch (DecryptionException expected) {
			assertEquals(INVALID_CIPHERTEXT, expected.getDecryptionResult());
		}
	}

	@Test
	public void testInvalidPasswordThrowsException() {
		byte[] input = getRandomBytes(1234);
		byte[] ciphertext = crypto.encryptWithPassword(input, "password", null);

		// Try to decrypt with the wrong password
		try {
			crypto.decryptWithPassword(ciphertext, "wrong", null);
			fail();
		} catch (DecryptionException expected) {
			assertEquals(INVALID_PASSWORD, expected.getDecryptionResult());
		}
	}

	@Test
	public void testMissingKeyStrengthenerThrowsException() {
		SecretKey strengthened = getSecretKey();
		context.checking(new Expectations() {{
			oneOf(keyStrengthener).strengthenKey(with(any(SecretKey.class)));
			will(returnValue(strengthened));
		}});

		// Use the key strengthener during encryption
		byte[] input = getRandomBytes(1234);
		String password = "password";
		byte[] ciphertext =
				crypto.encryptWithPassword(input, password, keyStrengthener);

		// The key strengthener is missing during decryption
		try {
			crypto.decryptWithPassword(ciphertext, password, null);
			fail();
		} catch (DecryptionException expected) {
			assertEquals(KEY_STRENGTHENER_ERROR, expected.getDecryptionResult());
		}
	}

	@Test
	public void testKeyStrengthenerFailureThrowsException() {
		SecretKey strengthened = getSecretKey();
		context.checking(new Expectations() {{
			oneOf(keyStrengthener).strengthenKey(with(any(SecretKey.class)));
			will(returnValue(strengthened));
			oneOf(keyStrengthener).isInitialised();
			will(returnValue(false));
		}});

		// Use the key strengthener during encryption
		byte[] input = getRandomBytes(1234);
		String password = "password";
		byte[] ciphertext =
				crypto.encryptWithPassword(input, password, keyStrengthener);

		// The key strengthener fails during decryption
		try {
			crypto.decryptWithPassword(ciphertext, password, keyStrengthener);
			fail();
		} catch (DecryptionException expected) {
			assertEquals(KEY_STRENGTHENER_ERROR, expected.getDecryptionResult());
		}
	}
}

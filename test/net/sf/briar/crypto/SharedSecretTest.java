package net.sf.briar.crypto;

import java.util.Arrays;
import java.util.Random;

import junit.framework.TestCase;

import org.junit.Test;

public class SharedSecretTest extends TestCase {

	@Test
	public void testDecodeAndEncode() {
		Random random = new Random();
		byte[] secret = new byte[40];
		random.nextBytes(secret);
		secret[SharedSecret.IV_BYTES] = (byte) 0;
		SharedSecret s = new SharedSecret(secret);
		assertTrue(Arrays.equals(secret, s.getBytes()));
		secret[SharedSecret.IV_BYTES] = (byte) 1;
		s = new SharedSecret(secret);
		assertTrue(Arrays.equals(secret, s.getBytes()));
		// The Alice flag must be either 0 or 1
		secret[SharedSecret.IV_BYTES] = (byte) 2;
		try {
			s = new SharedSecret(secret);
			fail();
		} catch(IllegalArgumentException expected) {}
		// The ciphertext must be at least 1 byte long
		secret = new byte[SharedSecret.IV_BYTES + 1];
		random.nextBytes(secret);
		secret[SharedSecret.IV_BYTES] = (byte) 0;
		try {
			s = new SharedSecret(secret);
			fail();
		} catch(IllegalArgumentException expected) {}
	}
}

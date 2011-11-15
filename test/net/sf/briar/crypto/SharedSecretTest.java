package net.sf.briar.crypto;

import static org.junit.Assert.assertArrayEquals;

import java.util.Random;

import junit.framework.TestCase;

import org.junit.Test;

public class SharedSecretTest extends TestCase {

	@Test
	public void testDecodeAndEncode() {
		Random random = new Random();
		byte[] secret = new byte[40];
		random.nextBytes(secret);
		secret[0] = (byte) 0;
		SharedSecret s = new SharedSecret(secret);
		assertArrayEquals(secret, s.getBytes());
		secret[0] = (byte) 1;
		s = new SharedSecret(secret);
		assertArrayEquals(secret, s.getBytes());
		// The Alice flag must be either 0 or 1
		secret[0] = (byte) 2;
		try {
			s = new SharedSecret(secret);
			fail();
		} catch(IllegalArgumentException expected) {}
		// The secret must be at least 1 byte long
		secret = new byte[1];
		random.nextBytes(secret);
		secret[0] = (byte) 0;
		try {
			s = new SharedSecret(secret);
			fail();
		} catch(IllegalArgumentException expected) {}
	}
}

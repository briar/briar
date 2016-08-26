package org.briarproject.crypto;

import org.briarproject.BriarTestCase;
import org.briarproject.TestSeedProvider;
import org.briarproject.TestUtils;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

public class MacTest extends BriarTestCase {

	private final CryptoComponent crypto;

	public MacTest() {
		crypto = new CryptoComponentImpl(new TestSeedProvider());
	}

	@Test
	public void testIdenticalKeysAndInputsProduceIdenticalMacs() {
		// Generate a random key and some random input
		byte[] keyBytes = TestUtils.getRandomBytes(SecretKey.LENGTH);
		SecretKey k = new SecretKey(keyBytes);
		byte[] inputBytes = TestUtils.getRandomBytes(123);
		byte[] inputBytes1 = TestUtils.getRandomBytes(234);
		byte[] inputBytes2 = new byte[0];
		// Calculate the MAC twice - the results should be identical
		byte[] mac = crypto.mac(k, inputBytes, inputBytes1, inputBytes2);
		byte[] mac1 = crypto.mac(k, inputBytes, inputBytes1, inputBytes2);
		assertArrayEquals(mac, mac1);
	}

	@Test
	public void testDifferentKeysProduceDifferentMacs() {
		// Generate two random keys and some random input
		byte[] keyBytes = TestUtils.getRandomBytes(SecretKey.LENGTH);
		SecretKey k = new SecretKey(keyBytes);
		byte[] keyBytes1 = TestUtils.getRandomBytes(SecretKey.LENGTH);
		SecretKey k1 = new SecretKey(keyBytes1);
		byte[] inputBytes = TestUtils.getRandomBytes(123);
		byte[] inputBytes1 = TestUtils.getRandomBytes(234);
		byte[] inputBytes2 = new byte[0];
		// Calculate the MAC with each key - the results should be different
		byte[] mac = crypto.mac(k, inputBytes, inputBytes1, inputBytes2);
		byte[] mac1 = crypto.mac(k1, inputBytes, inputBytes1, inputBytes2);
		assertFalse(Arrays.equals(mac, mac1));
	}

	@Test
	public void testDifferentInputsProduceDifferentMacs() {
		// Generate a random key and some random input
		byte[] keyBytes = TestUtils.getRandomBytes(SecretKey.LENGTH);
		SecretKey k = new SecretKey(keyBytes);
		byte[] inputBytes = TestUtils.getRandomBytes(123);
		byte[] inputBytes1 = TestUtils.getRandomBytes(234);
		byte[] inputBytes2 = new byte[0];
		// Calculate the MAC with the inputs in different orders - the results
		// should be different
		byte[] mac = crypto.mac(k, inputBytes, inputBytes1, inputBytes2);
		byte[] mac1 = crypto.mac(k, inputBytes2, inputBytes1, inputBytes);
		assertFalse(Arrays.equals(mac, mac1));
	}
}

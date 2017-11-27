package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestSecureRandomProvider;
import org.junit.Test;

import java.util.Arrays;

import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

public class MacTest extends BrambleTestCase {

	private final CryptoComponent crypto =
			new CryptoComponentImpl(new TestSecureRandomProvider());

	private final SecretKey key1 = getSecretKey(), key2 = getSecretKey();
	private final String label1 = getRandomString(123);
	private final String label2 = getRandomString(123);
	private final byte[] input1 = getRandomBytes(123);
	private final byte[] input2 = getRandomBytes(234);
	private final byte[] input3 = new byte[0];

	@Test
	public void testIdenticalKeysAndInputsProduceIdenticalMacs() {
		// Calculate the MAC twice - the results should be identical
		byte[] mac = crypto.mac(label1, key1, input1, input2, input3);
		byte[] mac1 = crypto.mac(label1, key1, input1, input2, input3);
		assertArrayEquals(mac, mac1);
	}

	@Test
	public void testDifferentLabelsProduceDifferentMacs() {
		// Calculate the MAC with each label - the results should be different
		byte[] mac = crypto.mac(label1, key1, input1, input2, input3);
		byte[] mac1 = crypto.mac(label2, key1, input1, input2, input3);
		assertFalse(Arrays.equals(mac, mac1));
	}

	@Test
	public void testDifferentKeysProduceDifferentMacs() {
		// Calculate the MAC with each key - the results should be different
		byte[] mac = crypto.mac(label1, key1, input1, input2, input3);
		byte[] mac1 = crypto.mac(label1, key2, input1, input2, input3);
		assertFalse(Arrays.equals(mac, mac1));
	}

	@Test
	public void testDifferentInputsProduceDifferentMacs() {
		// Calculate the MAC with the inputs in different orders - the results
		// should be different
		byte[] mac = crypto.mac(label1, key1, input1, input2, input3);
		byte[] mac1 = crypto.mac(label1, key1, input3, input2, input1);
		assertFalse(Arrays.equals(mac, mac1));
	}

}

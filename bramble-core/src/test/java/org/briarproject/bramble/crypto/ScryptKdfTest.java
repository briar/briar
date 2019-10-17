package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.system.SystemClock;
import org.briarproject.bramble.test.ArrayClock;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static junit.framework.TestCase.assertTrue;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertEquals;

public class ScryptKdfTest extends BrambleTestCase {

	@Test
	public void testPasswordAffectsKey() throws Exception {
		PasswordBasedKdf kdf = new ScryptKdf(new SystemClock());
		byte[] salt = getRandomBytes(32);
		Set<Bytes> keys = new HashSet<>();
		for (int i = 0; i < 100; i++) {
			String password = getRandomString(16);
			SecretKey key = kdf.deriveKey(password, salt, 256);
			assertTrue(keys.add(new Bytes(key.getBytes())));
		}
	}

	@Test
	public void testSaltAffectsKey() throws Exception {
		PasswordBasedKdf kdf = new ScryptKdf(new SystemClock());
		String password = getRandomString(16);
		Set<Bytes> keys = new HashSet<>();
		for (int i = 0; i < 100; i++) {
			byte[] salt = getRandomBytes(32);
			SecretKey key = kdf.deriveKey(password, salt, 256);
			assertTrue(keys.add(new Bytes(key.getBytes())));
		}
	}

	@Test
	public void testCostParameterAffectsKey() throws Exception {
		PasswordBasedKdf kdf = new ScryptKdf(new SystemClock());
		String password = getRandomString(16);
		byte[] salt = getRandomBytes(32);
		Set<Bytes> keys = new HashSet<>();
		for (int cost = 2; cost <= 256; cost *= 2) {
			SecretKey key = kdf.deriveKey(password, salt, cost);
			assertTrue(keys.add(new Bytes(key.getBytes())));
		}
	}

	@Test
	public void testCalibration() throws Exception {
		Clock clock = new ArrayClock(
				0, 50, // Duration for cost 256
				0, 100, // Duration for cost 512
				0, 200, // Duration for cost 1024
				0, 400, // Duration for cost 2048
				0, 800 // Duration for cost 4096
		);
		PasswordBasedKdf kdf = new ScryptKdf(clock);
		assertEquals(4096, kdf.chooseCostParameter());
	}

	@Test
	public void testCalibrationChoosesMinCost() throws Exception {
		Clock clock = new ArrayClock(
				0, 2000 // Duration for cost 256 is already too high
		);
		PasswordBasedKdf kdf = new ScryptKdf(clock);
		assertEquals(256, kdf.chooseCostParameter());
	}
}

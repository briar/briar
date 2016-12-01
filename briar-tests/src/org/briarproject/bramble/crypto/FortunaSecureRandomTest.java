package org.briarproject.bramble.crypto;

import org.briarproject.BriarTestCase;
import org.briarproject.bramble.api.crypto.MessageDigest;
import org.junit.Test;
import org.spongycastle.crypto.BlockCipher;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.engines.AESLightEngine;
import org.spongycastle.crypto.params.KeyParameter;

import static org.briarproject.bramble.crypto.FortunaSecureRandom.SELF_TEST_VECTOR_1;
import static org.briarproject.bramble.crypto.FortunaSecureRandom.SELF_TEST_VECTOR_2;
import static org.briarproject.bramble.crypto.FortunaSecureRandom.SELF_TEST_VECTOR_3;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class FortunaSecureRandomTest extends BriarTestCase {

	@Test
	public void testClassPassesSelfTest() {
		assertTrue(FortunaSecureRandom.selfTest());
	}

	@Test
	public void testSelfTestVectorsAreReproducible() {
		byte[] key = new byte[32], seed = new byte[32];
		byte[] counter = new byte[16], output = new byte[16];
		byte[] newKey = new byte[32];
		// Calculate the initial key
		MessageDigest digest = new DoubleDigest(new SHA256Digest());
		digest.update(key);
		digest.update(seed);
		digest.digest(key, 0, 32);
		// Calculate the first output block and the new key
		BlockCipher c = new AESLightEngine();
		c.init(true, new KeyParameter(key));
		counter[0] = 1;
		c.processBlock(counter, 0, output, 0);
		counter[0] = 2;
		c.processBlock(counter, 0, newKey, 0);
		counter[0] = 3;
		c.processBlock(counter, 0, newKey, 16);
		System.arraycopy(newKey, 0, key, 0, 32);
		// The first self-test vector should match the first output block
		assertArrayEquals(SELF_TEST_VECTOR_1, output);
		// Calculate the second output block and the new key before reseeding
		c.init(true, new KeyParameter(key));
		counter[0] = 4;
		c.processBlock(counter, 0, output, 0);
		counter[0] = 5;
		c.processBlock(counter, 0, newKey, 0);
		counter[0] = 6;
		c.processBlock(counter, 0, newKey, 16);
		System.arraycopy(newKey, 0, key, 0, 32);
		// The second self-test vector should match the second output block
		assertArrayEquals(SELF_TEST_VECTOR_2, output);
		// Calculate the new key after reseeding
		digest.update(key);
		digest.update(seed);
		digest.digest(key, 0, 32);
		// Calculate the third output block
		c.init(true, new KeyParameter(key));
		counter[0] = 8;
		c.processBlock(counter, 0, output, 0);
		// The third self-test vector should match the third output block
		assertArrayEquals(SELF_TEST_VECTOR_3, output);
	}
}

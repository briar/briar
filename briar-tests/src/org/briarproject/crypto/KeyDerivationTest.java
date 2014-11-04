package org.briarproject.crypto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.briarproject.BriarTestCase;
import org.briarproject.TestSeedProvider;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.junit.Test;

public class KeyDerivationTest extends BriarTestCase {

	private final CryptoComponent crypto;
	private final byte[] secret;

	public KeyDerivationTest() {
		crypto = new CryptoComponentImpl(new TestSeedProvider());
		secret = new byte[32];
		new Random().nextBytes(secret);
	}

	@Test
	public void testKeysAreDistinct() {
		List<SecretKey> keys = new ArrayList<SecretKey>();
		keys.add(crypto.deriveFrameKey(secret, 0, true));
		keys.add(crypto.deriveFrameKey(secret, 0, false));
		keys.add(crypto.deriveTagKey(secret, true));
		keys.add(crypto.deriveTagKey(secret, false));
		for(int i = 0; i < 4; i++) {
			byte[] keyI = keys.get(i).getEncoded();
			for(int j = 0; j < 4; j++) {
				byte[] keyJ = keys.get(j).getEncoded();
				assertEquals(i == j, Arrays.equals(keyI, keyJ));
			}
		}
	}

	@Test
	public void testSecretAffectsDerivation() {
		Random r = new Random();
		List<byte[]> secrets = new ArrayList<byte[]>();
		for(int i = 0; i < 20; i++) {
			byte[] b = new byte[32];
			r.nextBytes(b);
			secrets.add(crypto.deriveNextSecret(b, 0));
		}
		for(int i = 0; i < 20; i++) {
			byte[] secretI = secrets.get(i);
			for(int j = 0; j < 20; j++) {
				byte[] secretJ = secrets.get(j);
				assertEquals(i == j, Arrays.equals(secretI, secretJ));
			}
		}
	}

	@Test
	public void testStreamNumberAffectsDerivation() {
		List<byte[]> secrets = new ArrayList<byte[]>();
		for(int i = 0; i < 20; i++) {
			secrets.add(crypto.deriveNextSecret(secret.clone(), i));
		}
		for(int i = 0; i < 20; i++) {
			byte[] secretI = secrets.get(i);
			for(int j = 0; j < 20; j++) {
				byte[] secretJ = secrets.get(j);
				assertEquals(i == j, Arrays.equals(secretI, secretJ));
			}
		}
	}
}

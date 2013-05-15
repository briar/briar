package net.sf.briar.crypto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;

import org.junit.Test;

public class KeyDerivationTest extends BriarTestCase {

	private final CryptoComponent crypto;
	private final byte[] secret;

	public KeyDerivationTest() {
		crypto = new CryptoComponentImpl();
		secret = new byte[32];
		new Random().nextBytes(secret);
	}

	@Test
	public void testKeysAreDistinct() {
		List<ErasableKey> keys = new ArrayList<ErasableKey>();
		keys.add(crypto.deriveFrameKey(secret, 0, false, false));
		keys.add(crypto.deriveFrameKey(secret, 0, false, true));
		keys.add(crypto.deriveFrameKey(secret, 0, true, false));
		keys.add(crypto.deriveFrameKey(secret, 0, true, true));
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
	public void testConnectionNumberAffectsDerivation() {
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

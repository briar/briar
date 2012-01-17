package net.sf.briar.crypto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.protocol.ProtocolConstants;

import org.junit.Test;

public class KeyDerivationTest extends BriarTestCase {

	private final CryptoComponent crypto;
	private final byte[] secret;

	public KeyDerivationTest() {
		super();
		crypto = new CryptoComponentImpl();
		secret = new byte[32];
		new Random().nextBytes(secret);
	}

	@Test
	public void testSixKeysAreDistinct() {
		List<ErasableKey> keys = new ArrayList<ErasableKey>();
		keys.add(crypto.deriveSegmentKey(secret, true));
		keys.add(crypto.deriveSegmentKey(secret, false));
		keys.add(crypto.deriveTagKey(secret, true));
		keys.add(crypto.deriveTagKey(secret, false));
		keys.add(crypto.deriveMacKey(secret, true));
		keys.add(crypto.deriveMacKey(secret, false));
		for(int i = 0; i < 6; i++) {
			byte[] keyI = keys.get(i).getEncoded();
			for(int j = 0; j < 6; j++) {
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
			secrets.add(crypto.deriveNextSecret(b, 0, 0));
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
	public void testTransportIndexAffectsDerivation() {
		List<byte[]> secrets = new ArrayList<byte[]>();
		for(int i = 0; i < ProtocolConstants.MAX_TRANSPORTS; i++) {
			secrets.add(crypto.deriveNextSecret(secret, i, 0));
		}
		for(int i = 0; i < ProtocolConstants.MAX_TRANSPORTS; i++) {
			byte[] secretI = secrets.get(i);
			for(int j = 0; j < ProtocolConstants.MAX_TRANSPORTS; j++) {
				byte[] secretJ = secrets.get(j);
				assertEquals(i == j, Arrays.equals(secretI, secretJ));
			}
		}
	}

	@Test
	public void testConnectionNumberAffectsDerivation() {
		List<byte[]> secrets = new ArrayList<byte[]>();
		for(int i = 0; i < 20; i++) {
			secrets.add(crypto.deriveNextSecret(secret, 0, i));
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

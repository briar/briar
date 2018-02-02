package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestSecureRandomProvider;
import org.junit.Test;

import java.security.GeneralSecurityException;
import java.util.Random;

import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.SHARED_SECRET_LABEL;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.junit.Assert.assertArrayEquals;

public class KeyAgreementTest extends BrambleTestCase {

	private final CryptoComponent crypto =
			new CryptoComponentImpl(new TestSecureRandomProvider(), null);
	private final byte[][] inputs;

	public KeyAgreementTest() {
		Random random = new Random();
		inputs = new byte[random.nextInt(10) + 1][];
		for (int i = 0; i < inputs.length; i++)
			inputs[i] = getRandomBytes(random.nextInt(256));
	}

	@Test
	public void testDerivesSharedSecret() throws Exception {
		KeyPair aPair = crypto.generateAgreementKeyPair();
		KeyPair bPair = crypto.generateAgreementKeyPair();
		SecretKey aShared = crypto.deriveSharedSecret(SHARED_SECRET_LABEL,
				bPair.getPublic(), aPair, inputs);
		SecretKey bShared = crypto.deriveSharedSecret(SHARED_SECRET_LABEL,
				aPair.getPublic(), bPair, inputs);
		assertArrayEquals(aShared.getBytes(), bShared.getBytes());
	}

	@Test(expected = GeneralSecurityException.class)
	public void testRejectsInvalidPublicKey() throws Exception {
		KeyPair keyPair = crypto.generateAgreementKeyPair();
		PublicKey invalid = new Curve25519PublicKey(new byte[32]);
		crypto.deriveSharedSecret(SHARED_SECRET_LABEL, invalid, keyPair,
				inputs);
	}
}

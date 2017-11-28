package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestSecureRandomProvider;
import org.junit.Test;

import java.util.Random;

import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.SHARED_SECRET_LABEL;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.junit.Assert.assertArrayEquals;

public class KeyAgreementTest extends BrambleTestCase {

	@Test
	public void testDeriveSharedSecret() throws Exception {
		CryptoComponent crypto =
				new CryptoComponentImpl(new TestSecureRandomProvider());
		KeyPair aPair = crypto.generateAgreementKeyPair();
		KeyPair bPair = crypto.generateAgreementKeyPair();
		Random random = new Random();
		byte[][] inputs = new byte[random.nextInt(10) + 1][];
		for (int i = 0; i < inputs.length; i++)
			inputs[i] = getRandomBytes(random.nextInt(256));
		SecretKey aShared = crypto.deriveSharedSecret(SHARED_SECRET_LABEL,
				bPair.getPublic(), aPair, inputs);
		SecretKey bShared = crypto.deriveSharedSecret(SHARED_SECRET_LABEL,
				aPair.getPublic(), bPair, inputs);
		assertArrayEquals(aShared.getBytes(), bShared.getBytes());
	}
}

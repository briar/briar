package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestSecureRandomProvider;
import org.junit.Test;

import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.SHARED_SECRET_LABEL;
import static org.junit.Assert.assertArrayEquals;

public class KeyAgreementTest extends BrambleTestCase {

	@Test
	public void testDeriveSharedSecret() throws Exception {
		CryptoComponent crypto =
				new CryptoComponentImpl(new TestSecureRandomProvider());
		KeyPair aPair = crypto.generateAgreementKeyPair();
		KeyPair bPair = crypto.generateAgreementKeyPair();
		SecretKey aShared = crypto.deriveSharedSecret(SHARED_SECRET_LABEL,
				bPair.getPublic(), aPair, true);
		SecretKey bShared = crypto.deriveSharedSecret(SHARED_SECRET_LABEL,
				aPair.getPublic(), bPair, false);
		assertArrayEquals(aShared.getBytes(), bShared.getBytes());
	}
}

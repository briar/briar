package org.briarproject.crypto;

import static org.junit.Assert.assertArrayEquals;

import org.briarproject.BriarTestCase;
import org.briarproject.TestSeedProvider;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.SeedProvider;
import org.junit.Test;

public class KeyAgreementTest extends BriarTestCase {

	@Test
	public void testKeyAgreement() throws Exception {
		SeedProvider seedProvider = new TestSeedProvider();
		CryptoComponent crypto = new CryptoComponentImpl(seedProvider);
		KeyPair aPair = crypto.generateAgreementKeyPair();
		byte[] aPub = aPair.getPublic().getEncoded();
		KeyPair bPair = crypto.generateAgreementKeyPair();
		byte[] bPub = bPair.getPublic().getEncoded();
		byte[] aSecret = crypto.deriveMasterSecret(aPub, bPair, true);
		byte[] bSecret = crypto.deriveMasterSecret(bPub, aPair, false);
		assertArrayEquals(aSecret, bSecret);
	}
}

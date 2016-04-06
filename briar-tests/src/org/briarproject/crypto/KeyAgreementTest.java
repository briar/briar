package org.briarproject.crypto;

import org.briarproject.BriarTestCase;
import org.briarproject.TestSeedProvider;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.system.SeedProvider;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class KeyAgreementTest extends BriarTestCase {

	@Test
	public void testDeriveMasterSecret() throws Exception {
		SeedProvider seedProvider = new TestSeedProvider();
		CryptoComponent crypto = new CryptoComponentImpl(seedProvider);
		KeyPair aPair = crypto.generateAgreementKeyPair();
		byte[] aPub = aPair.getPublic().getEncoded();
		KeyPair bPair = crypto.generateAgreementKeyPair();
		byte[] bPub = bPair.getPublic().getEncoded();
		SecretKey aMaster = crypto.deriveMasterSecret(aPub, bPair, true);
		SecretKey bMaster = crypto.deriveMasterSecret(bPub, aPair, false);
		assertArrayEquals(aMaster.getBytes(), bMaster.getBytes());
	}

	@Test
	public void testDeriveSharedSecret() throws Exception {
		SeedProvider seedProvider = new TestSeedProvider();
		CryptoComponent crypto = new CryptoComponentImpl(seedProvider);
		KeyPair aPair = crypto.generateAgreementKeyPair();
		byte[] aPub = aPair.getPublic().getEncoded();
		KeyPair bPair = crypto.generateAgreementKeyPair();
		byte[] bPub = bPair.getPublic().getEncoded();
		SecretKey aShared = crypto.deriveSharedSecret(bPub, aPair, true);
		SecretKey bShared = crypto.deriveSharedSecret(aPub, bPair, false);
		assertArrayEquals(aShared.getBytes(), bShared.getBytes());
	}
}

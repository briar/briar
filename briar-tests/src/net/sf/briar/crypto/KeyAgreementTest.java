package net.sf.briar.crypto;

import static org.junit.Assert.assertArrayEquals;

import java.security.KeyPair;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;

import org.junit.Test;

public class KeyAgreementTest extends BriarTestCase {

	@Test
	public void testKeyAgreement() throws Exception {
		CryptoComponent crypto = new CryptoComponentImpl();
		KeyPair aPair = crypto.generateAgreementKeyPair();
		byte[] aPub = aPair.getPublic().getEncoded();
		KeyPair bPair = crypto.generateAgreementKeyPair();
		byte[] bPub = bPair.getPublic().getEncoded();
		byte[] aSecret = crypto.deriveMasterSecret(aPub, bPair, true);
		byte[] bSecret = crypto.deriveMasterSecret(bPub, aPair, false);
		assertArrayEquals(aSecret, bSecret);
	}
}

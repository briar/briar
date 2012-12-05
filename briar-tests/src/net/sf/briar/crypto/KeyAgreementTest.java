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
		KeyPair a = crypto.generateAgreementKeyPair();
		byte[] aPub = a.getPublic().getEncoded();
		KeyPair b = crypto.generateAgreementKeyPair();
		byte[] bPub = b.getPublic().getEncoded();
		byte[] aSecret = crypto.deriveInitialSecret(aPub, b, true);
		byte[] bSecret = crypto.deriveInitialSecret(bPub, a, false);
		assertArrayEquals(aSecret, bSecret);
	}
}

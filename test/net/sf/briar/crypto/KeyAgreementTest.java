package net.sf.briar.crypto;

import static org.junit.Assert.assertArrayEquals;

import java.security.KeyPair;
import java.security.PrivateKey;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;

import org.junit.Test;

public class KeyAgreementTest extends BriarTestCase {

	@Test
	public void testKeyAgreement() {
		CryptoComponent crypto = new CryptoComponentImpl();
		KeyPair a = crypto.generateAgreementKeyPair();
		byte[] aPub = a.getPublic().getEncoded();
		PrivateKey aPriv = a.getPrivate();
		KeyPair b = crypto.generateAgreementKeyPair();
		byte[] bPub = b.getPublic().getEncoded();
		PrivateKey bPriv = b.getPrivate();
		byte[] aSecret = crypto.deriveInitialSecret(aPub, bPub, aPriv, true);
		byte[] bSecret = crypto.deriveInitialSecret(bPub, aPub, bPriv, false);
		assertArrayEquals(aSecret, bSecret);
	}
}

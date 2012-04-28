package net.sf.briar.plugins;

import static org.junit.Assert.assertArrayEquals;

import java.security.KeyPair;
import java.security.PrivateKey;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class InvitationStarterImplTest extends BriarTestCase {

	private final CryptoComponent crypto;

	public InvitationStarterImplTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		crypto = i.getInstance(CryptoComponent.class);
	}

	@Test
	public void testKeyAgreement() {
		KeyPair a = crypto.generateAgreementKeyPair();
		byte[] aPub = a.getPublic().getEncoded();
		PrivateKey aPriv = a.getPrivate();
		KeyPair b = crypto.generateAgreementKeyPair();
		byte[] bPub = b.getPublic().getEncoded();
		PrivateKey bPriv = b.getPrivate();
		byte[][] aSecrets = crypto.deriveInitialSecrets(aPub, bPub, aPriv, 123,
				true);
		byte[][] bSecrets = crypto.deriveInitialSecrets(bPub, aPub, bPriv, 123,
				false);
		assertEquals(2, aSecrets.length);
		assertEquals(2, bSecrets.length);
		assertArrayEquals(aSecrets[0], bSecrets[0]);
		assertArrayEquals(aSecrets[1], bSecrets[1]);
	}
}

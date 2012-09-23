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

	// FIXME: This is actually a test of CryptoComponent

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
		byte[] aSecret = crypto.deriveInitialSecret(aPub, bPub, aPriv, true);
		byte[] bSecret = crypto.deriveInitialSecret(bPub, aPub, bPriv, false);
		assertArrayEquals(aSecret, bSecret);
	}
}

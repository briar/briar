package net.sf.briar.crypto;

import static org.junit.Assert.assertArrayEquals;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.KeyParser;

import org.junit.Test;

public class KeyEncodingAndParsingTest extends BriarTestCase {

	private final CryptoComponentImpl crypto = new CryptoComponentImpl();

	@Test
	public void testAgreementPublicKeyEncodingAndParsing() throws Exception {
		KeyParser parser = crypto.getAgreementKeyParser();
		// Generate two key pairs
		KeyPair aPair = crypto.generateAgreementKeyPair();
		KeyPair bPair = crypto.generateAgreementKeyPair();
		// Derive the shared secret
		PublicKey aPub = aPair.getPublic();
		byte[] secret = crypto.deriveSharedSecret(bPair.getPrivate(), aPub);
		// Encode and parse the public key - no exceptions should be thrown
		aPub = parser.parsePublicKey(aPub.getEncoded());
		aPub = parser.parsePublicKey(aPub.getEncoded());
		// Derive the shared secret again - it should be the same
		byte[] secret1 = crypto.deriveSharedSecret(bPair.getPrivate(), aPub);
		assertArrayEquals(secret, secret1);
	}

	@Test
	public void testAgreementPrivateKeyEncodingAndParsing() throws Exception {
		KeyParser parser = crypto.getAgreementKeyParser();
		// Generate two key pairs
		KeyPair aPair = crypto.generateAgreementKeyPair();
		KeyPair bPair = crypto.generateAgreementKeyPair();
		// Derive the shared secret
		PrivateKey bPriv = bPair.getPrivate();
		byte[] secret = crypto.deriveSharedSecret(bPriv, aPair.getPublic());
		// Encode and parse the private key - no exceptions should be thrown
		bPriv = parser.parsePrivateKey(bPriv.getEncoded());
		bPriv = parser.parsePrivateKey(bPriv.getEncoded());
		// Derive the shared secret again - it should be the same
		byte[] secret1 = crypto.deriveSharedSecret(bPriv, aPair.getPublic());
		assertArrayEquals(secret, secret1);
	}

	@Test
	public void testSignaturePublicKeyEncodingAndParsing() throws Exception {
		KeyParser parser = crypto.getSignatureKeyParser();
		// Generate two key pairs
		KeyPair aPair = crypto.generateSignatureKeyPair();
		KeyPair bPair = crypto.generateSignatureKeyPair();
		// Derive the shared secret
		PublicKey aPub = aPair.getPublic();
		byte[] secret = crypto.deriveSharedSecret(bPair.getPrivate(), aPub);
		// Encode and parse the public key - no exceptions should be thrown
		aPub = parser.parsePublicKey(aPub.getEncoded());
		aPub = parser.parsePublicKey(aPub.getEncoded());
		// Derive the shared secret again - it should be the same
		byte[] secret1 = crypto.deriveSharedSecret(bPair.getPrivate(), aPub);
		assertArrayEquals(secret, secret1);
	}

	@Test
	public void testSignaturePrivateKeyEncodingAndParsing() throws Exception {
		KeyParser parser = crypto.getSignatureKeyParser();
		// Generate two key pairs
		KeyPair aPair = crypto.generateSignatureKeyPair();
		KeyPair bPair = crypto.generateSignatureKeyPair();
		// Derive the shared secret
		PrivateKey bPriv = bPair.getPrivate();
		byte[] secret = crypto.deriveSharedSecret(bPriv, aPair.getPublic());
		// Encode and parse the private key - no exceptions should be thrown
		bPriv = parser.parsePrivateKey(bPriv.getEncoded());
		bPriv = parser.parsePrivateKey(bPriv.getEncoded());
		// Derive the shared secret again - it should be the same
		byte[] secret1 = crypto.deriveSharedSecret(bPriv, aPair.getPublic());
		assertArrayEquals(secret, secret1);
	}
}

package org.briarproject.crypto;

import static org.junit.Assert.assertArrayEquals;

import java.security.GeneralSecurityException;
import java.util.Random;

import org.briarproject.BriarTestCase;
import org.briarproject.TestSeedProvider;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.crypto.PublicKey;

import org.junit.Test;

public class KeyEncodingAndParsingTest extends BriarTestCase {

	private final CryptoComponentImpl crypto =
			new CryptoComponentImpl(new TestSeedProvider());

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
	public void testAgreementKeyParserByFuzzing() throws Exception {
		KeyParser parser = crypto.getAgreementKeyParser();
		// Generate a key pair to get the proper public key length
		KeyPair p = crypto.generateAgreementKeyPair();
		int pubLength = p.getPublic().getEncoded().length;
		int privLength = p.getPrivate().getEncoded().length;
		// Parse some random byte arrays - expect GeneralSecurityException
		Random random = new Random();
		byte[] pubFuzz = new byte[pubLength];
		byte[] privFuzz = new byte[privLength];
		for(int i = 0; i < 1000; i++) {
			random.nextBytes(pubFuzz);
			try {
				parser.parsePublicKey(pubFuzz);
			} catch(GeneralSecurityException expected) {
			} catch(Exception e) {
				fail();
			}
			random.nextBytes(privFuzz);
			try {
				parser.parsePrivateKey(privFuzz);
			} catch(GeneralSecurityException expected) {
			} catch(Exception e) {
				fail();
			}
		}
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

	@Test
	public void testSignatureKeyParserByFuzzing() throws Exception {
		KeyParser parser = crypto.getSignatureKeyParser();
		// Generate a key pair to get the proper public key length
		KeyPair p = crypto.generateSignatureKeyPair();
		int pubLength = p.getPublic().getEncoded().length;
		int privLength = p.getPrivate().getEncoded().length;
		// Parse some random byte arrays - expect GeneralSecurityException
		Random random = new Random();
		byte[] pubFuzz = new byte[pubLength];
		byte[] privFuzz = new byte[privLength];
		for(int i = 0; i < 1000; i++) {
			random.nextBytes(pubFuzz);
			try {
				parser.parsePublicKey(pubFuzz);
			} catch(GeneralSecurityException expected) {
			} catch(Exception e) {
				fail();
			}
			random.nextBytes(privFuzz);
			try {
				parser.parsePrivateKey(privFuzz);
			} catch(GeneralSecurityException expected) {
			} catch(Exception e) {
				fail();
			}
		}
	}
}

package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.KeyParser;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestSecureRandomProvider;
import org.briarproject.bramble.test.TestUtils;
import org.junit.Test;

import java.security.GeneralSecurityException;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class KeyEncodingAndParsingTest extends BrambleTestCase {

	private final CryptoComponentImpl crypto =
			new CryptoComponentImpl(new TestSecureRandomProvider());

	@Test
	public void testAgreementPublicKeyLength() throws Exception {
		// Generate 10 agreement key pairs
		for (int i = 0; i < 10; i++) {
			KeyPair keyPair = crypto.generateSignatureKeyPair();
			// Check the length of the public key
			byte[] publicKey = keyPair.getPublic().getEncoded();
			assertTrue(publicKey.length <= MAX_PUBLIC_KEY_LENGTH);
		}
	}

	@Test
	public void testAgreementPublicKeyEncodingAndParsing() throws Exception {
		KeyParser parser = crypto.getAgreementKeyParser();
		// Generate two key pairs
		KeyPair aPair = crypto.generateAgreementKeyPair();
		KeyPair bPair = crypto.generateAgreementKeyPair();
		// Derive the shared secret
		PublicKey aPub = aPair.getPublic();
		byte[] secret = crypto.performRawKeyAgreement(bPair.getPrivate(), aPub);
		// Encode and parse the public key - no exceptions should be thrown
		aPub = parser.parsePublicKey(aPub.getEncoded());
		aPub = parser.parsePublicKey(aPub.getEncoded());
		// Derive the shared secret again - it should be the same
		byte[] secret1 = crypto.performRawKeyAgreement(bPair.getPrivate(), aPub);
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
		byte[] secret = crypto.performRawKeyAgreement(bPriv, aPair.getPublic());
		// Encode and parse the private key - no exceptions should be thrown
		bPriv = parser.parsePrivateKey(bPriv.getEncoded());
		bPriv = parser.parsePrivateKey(bPriv.getEncoded());
		// Derive the shared secret again - it should be the same
		byte[] secret1 = crypto.performRawKeyAgreement(bPriv, aPair.getPublic());
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
		for (int i = 0; i < 1000; i++) {
			try {
				parser.parsePublicKey(TestUtils.getRandomBytes(pubLength));
			} catch (GeneralSecurityException expected) {
				// Expected
			}
			try {
				parser.parsePrivateKey(TestUtils.getRandomBytes(privLength));
			} catch (GeneralSecurityException expected) {
				// Expected
			}
		}
	}

	@Test
	public void testSignaturePublicKeyLength() throws Exception {
		// Generate 10 signature key pairs
		for (int i = 0; i < 10; i++) {
			KeyPair keyPair = crypto.generateSignatureKeyPair();
			// Check the length of the public key
			byte[] publicKey = keyPair.getPublic().getEncoded();
			assertTrue(publicKey.length <= MAX_PUBLIC_KEY_LENGTH);
		}
	}

	@Test
	public void testSignatureLength() throws Exception {
		// Generate 10 signature key pairs
		for (int i = 0; i < 10; i++) {
			KeyPair keyPair = crypto.generateSignatureKeyPair();
			byte[] key = keyPair.getPrivate().getEncoded();
			// Sign some random data and check the length of the signature
			byte[] toBeSigned = TestUtils.getRandomBytes(1234);
			byte[] signature = crypto.sign("label", toBeSigned, key);
			assertTrue(signature.length <= MAX_SIGNATURE_LENGTH);
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
		byte[] secret = crypto.performRawKeyAgreement(bPair.getPrivate(), aPub);
		// Encode and parse the public key - no exceptions should be thrown
		aPub = parser.parsePublicKey(aPub.getEncoded());
		aPub = parser.parsePublicKey(aPub.getEncoded());
		// Derive the shared secret again - it should be the same
		byte[] secret1 = crypto.performRawKeyAgreement(bPair.getPrivate(), aPub);
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
		byte[] secret = crypto.performRawKeyAgreement(bPriv, aPair.getPublic());
		// Encode and parse the private key - no exceptions should be thrown
		bPriv = parser.parsePrivateKey(bPriv.getEncoded());
		bPriv = parser.parsePrivateKey(bPriv.getEncoded());
		// Derive the shared secret again - it should be the same
		byte[] secret1 = crypto.performRawKeyAgreement(bPriv, aPair.getPublic());
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
		for (int i = 0; i < 1000; i++) {
			try {
				parser.parsePublicKey(TestUtils.getRandomBytes(pubLength));
			} catch (GeneralSecurityException expected) {
				// Expected
			}
			try {
				parser.parsePrivateKey(TestUtils.getRandomBytes(privLength));
			} catch (GeneralSecurityException expected) {
				// Expected
			}
		}
	}
}

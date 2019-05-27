package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.AgreementPublicKey;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestSecureRandomProvider;
import org.junit.Test;
import org.whispersystems.curve25519.Curve25519;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Random;

import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.util.StringUtils.fromHexString;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

public class KeyAgreementTest extends BrambleTestCase {

	// Test vector from RFC 7748: Alice's private and public keys, Bob's
	// private and public keys, and the shared secret
	// https://tools.ietf.org/html/rfc7748#section-6.1
	private static final String ALICE_PRIVATE =
			"77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a";
	private static final String ALICE_PUBLIC =
			"8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a";
	private static final String BOB_PRIVATE =
			"5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb";
	private static final String BOB_PUBLIC =
			"de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f";
	private static final String SHARED_SECRET =
			"4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742";

	private final CryptoComponent crypto =
			new CryptoComponentImpl(new TestSecureRandomProvider(), null);
	private final String label = getRandomString(123);
	private final byte[][] inputs;

	public KeyAgreementTest() {
		Random random = new Random();
		inputs = new byte[random.nextInt(10) + 1][];
		for (int i = 0; i < inputs.length; i++)
			inputs[i] = getRandomBytes(random.nextInt(256));
	}

	@Test
	public void testDerivesSharedSecret() throws Exception {
		KeyPair aPair = crypto.generateAgreementKeyPair();
		KeyPair bPair = crypto.generateAgreementKeyPair();
		SecretKey aShared = crypto.deriveSharedSecret(label,
				bPair.getPublic(), aPair, inputs);
		SecretKey bShared = crypto.deriveSharedSecret(label,
				aPair.getPublic(), bPair, inputs);
		assertArrayEquals(aShared.getBytes(), bShared.getBytes());
	}

	@Test
	public void testDerivesStaticEphemeralSharedSecret() throws Exception {
		String label = getRandomString(123);
		KeyPair aStatic = crypto.generateAgreementKeyPair();
		KeyPair aEphemeral = crypto.generateAgreementKeyPair();
		KeyPair bStatic = crypto.generateAgreementKeyPair();
		KeyPair bEphemeral = crypto.generateAgreementKeyPair();
		SecretKey aShared = crypto.deriveSharedSecret(label,
				bStatic.getPublic(), bEphemeral.getPublic(), aStatic,
				aEphemeral, true, inputs);
		SecretKey bShared = crypto.deriveSharedSecret(label,
				aStatic.getPublic(), aEphemeral.getPublic(), bStatic,
				bEphemeral, false, inputs);
		assertArrayEquals(aShared.getBytes(), bShared.getBytes());
	}

	@Test(expected = GeneralSecurityException.class)
	public void testRejectsInvalidPublicKey() throws Exception {
		KeyPair keyPair = crypto.generateAgreementKeyPair();
		PublicKey invalid = new AgreementPublicKey(new byte[32]);
		crypto.deriveSharedSecret(label, invalid, keyPair, inputs);
	}

	@Test
	public void testRfc7748TestVector() {
		byte[] aPriv = parsePrivateKey(ALICE_PRIVATE);
		byte[] aPub = fromHexString(ALICE_PUBLIC);
		byte[] bPriv = parsePrivateKey(BOB_PRIVATE);
		byte[] bPub = fromHexString(BOB_PUBLIC);
		byte[] sharedSecret = fromHexString(SHARED_SECRET);
		Curve25519 curve25519 = Curve25519.getInstance("java");
		assertArrayEquals(sharedSecret,
				curve25519.calculateAgreement(aPub, bPriv));
		assertArrayEquals(sharedSecret,
				curve25519.calculateAgreement(bPub, aPriv));
	}

	@Test
	public void testDerivesSameSharedSecretFromEquivalentPublicKey() {
		byte[] aPub = fromHexString(ALICE_PUBLIC);
		byte[] bPriv = parsePrivateKey(BOB_PRIVATE);
		byte[] sharedSecret = fromHexString(SHARED_SECRET);
		Curve25519 curve25519 = Curve25519.getInstance("java");

		// Flip the unused most significant bit of the little-endian public key
		byte[] aPubEquiv = aPub.clone();
		aPubEquiv[31] ^= (byte) 128;

		// The public keys should be different but give the same shared secret
		assertFalse(Arrays.equals(aPub, aPubEquiv));
		assertArrayEquals(sharedSecret,
				curve25519.calculateAgreement(aPub, bPriv));
		assertArrayEquals(sharedSecret,
				curve25519.calculateAgreement(aPubEquiv, bPriv));
	}

	@Test
	public void testDerivesSameSharedSecretFromEquivalentPublicKeyWithoutPublicKeysHashedIn()
			throws Exception {
		KeyPair aPair = crypto.generateAgreementKeyPair();
		KeyPair bPair = crypto.generateAgreementKeyPair();

		// Flip the unused most significant bit of the little-endian public key
		byte[] aPub = aPair.getPublic().getEncoded();
		byte[] aPubEquiv = aPub.clone();
		aPubEquiv[31] ^= (byte) 128;
		KeyPair aPairEquiv = new KeyPair(new AgreementPublicKey(aPubEquiv),
				aPair.getPrivate());

		// The public keys should be different but give the same shared secret
		assertFalse(Arrays.equals(aPub, aPubEquiv));
		SecretKey shared = crypto.deriveSharedSecret(label,
				aPair.getPublic(), bPair);
		SecretKey sharedEquiv = crypto.deriveSharedSecret(label,
				aPairEquiv.getPublic(), bPair);
		assertArrayEquals(shared.getBytes(), sharedEquiv.getBytes());
	}

	@Test
	public void testDerivesDifferentSharedSecretFromEquivalentPublicKeyWithPublicKeysHashedIn()
			throws Exception {
		KeyPair aPair = crypto.generateAgreementKeyPair();
		KeyPair bPair = crypto.generateAgreementKeyPair();

		// Flip the unused most significant bit of the little-endian public key
		byte[] aPub = aPair.getPublic().getEncoded();
		byte[] aPubEquiv = aPub.clone();
		aPubEquiv[31] ^= (byte) 128;
		KeyPair aPairEquiv = new KeyPair(new AgreementPublicKey(aPubEquiv),
				aPair.getPrivate());

		// The public keys should be different and give different shared secrets
		assertFalse(Arrays.equals(aPub, aPubEquiv));
		SecretKey shared = deriveSharedSecretWithPublicKeysHashedIn(label,
				aPair.getPublic(), bPair);
		SecretKey sharedEquiv = deriveSharedSecretWithPublicKeysHashedIn(label,
				aPairEquiv.getPublic(), bPair);
		assertFalse(Arrays.equals(shared.getBytes(), sharedEquiv.getBytes()));
	}

	private SecretKey deriveSharedSecretWithPublicKeysHashedIn(String label,
			PublicKey publicKey, KeyPair keyPair) throws Exception {
		byte[][] inputs = new byte[][] {
				publicKey.getEncoded(),
				keyPair.getPublic().getEncoded()
		};
		return crypto.deriveSharedSecret(label, publicKey, keyPair, inputs);
	}

	private byte[] parsePrivateKey(String hex) {
		// Private keys need to be clamped because curve25519-java does the
		// clamping at key generation time, not multiplication time
		return AgreementKeyParser.clamp(fromHexString(hex));
	}
}

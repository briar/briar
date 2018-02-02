package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestSecureRandomProvider;
import org.junit.Test;
import org.whispersystems.curve25519.Curve25519;

import java.security.GeneralSecurityException;
import java.util.Random;

import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.SHARED_SECRET_LABEL;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.util.StringUtils.fromHexString;
import static org.junit.Assert.assertArrayEquals;

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
		SecretKey aShared = crypto.deriveSharedSecret(SHARED_SECRET_LABEL,
				bPair.getPublic(), aPair, inputs);
		SecretKey bShared = crypto.deriveSharedSecret(SHARED_SECRET_LABEL,
				aPair.getPublic(), bPair, inputs);
		assertArrayEquals(aShared.getBytes(), bShared.getBytes());
	}

	@Test(expected = GeneralSecurityException.class)
	public void testRejectsInvalidPublicKey() throws Exception {
		KeyPair keyPair = crypto.generateAgreementKeyPair();
		PublicKey invalid = new Curve25519PublicKey(new byte[32]);
		crypto.deriveSharedSecret(SHARED_SECRET_LABEL, invalid, keyPair,
				inputs);
	}

	@Test
	public void testRfc7748TestVector() throws Exception {
		// Private keys need to be clamped because curve25519-java does the
		// clamping at key generation time, not multiplication time
		byte[] aPriv = Curve25519KeyParser.clamp(fromHexString(ALICE_PRIVATE));
		byte[] aPub = fromHexString(ALICE_PUBLIC);
		byte[] bPriv = Curve25519KeyParser.clamp(fromHexString(BOB_PRIVATE));
		byte[] bPub = fromHexString(BOB_PUBLIC);
		byte[] sharedSecret = fromHexString(SHARED_SECRET);
		Curve25519 curve25519 = Curve25519.getInstance("java");
		assertArrayEquals(sharedSecret,
				curve25519.calculateAgreement(aPub, bPriv));
		assertArrayEquals(sharedSecret,
				curve25519.calculateAgreement(bPub, aPriv));
	}
}

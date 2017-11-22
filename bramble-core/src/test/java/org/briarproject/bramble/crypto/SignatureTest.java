package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestSecureRandomProvider;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.util.StringUtils;
import org.junit.Test;

import java.security.GeneralSecurityException;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public abstract class SignatureTest extends BrambleTestCase {

	protected final CryptoComponent crypto;

	private final byte[] publicKey, privateKey;
	private final String label = StringUtils.getRandomString(42);
	private final byte[] inputBytes = TestUtils.getRandomBytes(123);

	protected abstract KeyPair generateKeyPair();

	protected abstract byte[] sign(String label, byte[] toSign,
			byte[] privateKey) throws GeneralSecurityException;

	protected abstract boolean verify(String label, byte[] signedData,
			byte[] publicKey, byte[] signature) throws GeneralSecurityException;

	SignatureTest() {
		crypto = new CryptoComponentImpl(new TestSecureRandomProvider());
		KeyPair k = generateKeyPair();
		publicKey = k.getPublic().getEncoded();
		privateKey = k.getPrivate().getEncoded();
	}

	@Test
	public void testIdenticalKeysAndInputsProduceIdenticalSignatures()
			throws Exception {
		// Calculate the Signature twice - the results should be identical
		byte[] sig1 = sign(label, inputBytes, privateKey);
		byte[] sig2 = sign(label, inputBytes, privateKey);
		assertArrayEquals(sig1, sig2);
	}

	@Test
	public void testDifferentKeysProduceDifferentSignatures() throws Exception {
		// Generate second private key
		KeyPair k2 = generateKeyPair();
		byte[] privateKey2 = k2.getPrivate().getEncoded();
		// Calculate the signature with each key
		byte[] sig1 = sign(label, inputBytes, privateKey);
		byte[] sig2 = sign(label, inputBytes, privateKey2);
		assertFalse(Arrays.equals(sig1, sig2));
	}

	@Test
	public void testDifferentInputsProduceDifferentSignatures()
			throws Exception {
		// Generate a second input
		byte[] inputBytes2 = TestUtils.getRandomBytes(123);
		// Calculate the signature with different inputs
		// the results should be different
		byte[] sig1 = sign(label, inputBytes, privateKey);
		byte[] sig2 = sign(label, inputBytes2, privateKey);
		assertFalse(Arrays.equals(sig1, sig2));
	}

	@Test
	public void testDifferentLabelsProduceDifferentSignatures()
			throws Exception {
		// Generate a second label
		String label2 = StringUtils.getRandomString(42);
		// Calculate the signature with different inputs
		// the results should be different
		byte[] sig1 = sign(label, inputBytes, privateKey);
		byte[] sig2 = sign(label2, inputBytes, privateKey);
		assertFalse(Arrays.equals(sig1, sig2));
	}

	@Test
	public void testSignatureVerification() throws Exception {
		byte[] sig = sign(label, inputBytes, privateKey);
		assertTrue(verify(label, inputBytes, publicKey, sig));
	}

	@Test
	public void testDifferentKeyFailsVerification() throws Exception {
		// Generate second private key
		KeyPair k2 = generateKeyPair();
		byte[] privateKey2 = k2.getPrivate().getEncoded();
		// calculate the signature with different key, should fail to verify
		byte[] sig = sign(label, inputBytes, privateKey2);
		assertFalse(verify(label, inputBytes, publicKey, sig));
	}

	@Test
	public void testDifferentInputFailsVerification() throws Exception {
		// Generate a second input
		byte[] inputBytes2 = TestUtils.getRandomBytes(123);
		// calculate the signature with different input, should fail to verify
		byte[] sig = sign(label, inputBytes, privateKey);
		assertFalse(verify(label, inputBytes2, publicKey, sig));
	}

	@Test
	public void testDifferentLabelFailsVerification() throws Exception {
		// Generate a second label
		String label2 = StringUtils.getRandomString(42);
		// calculate the signature with different label, should fail to verify
		byte[] sig = sign(label, inputBytes, privateKey);
		assertFalse(verify(label2, inputBytes, publicKey, sig));
	}

}

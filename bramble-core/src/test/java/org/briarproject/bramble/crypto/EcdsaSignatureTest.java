package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.KeyPair;

import java.security.GeneralSecurityException;

public class EcdsaSignatureTest extends SignatureTest {

	@Override
	protected KeyPair generateKeyPair() {
		return crypto.generateSignatureKeyPair();
	}

	@Override
	protected byte[] sign(String label, byte[] toSign, byte[] privateKey)
			throws GeneralSecurityException {
		return crypto.sign(label, toSign, privateKey);
	}

	@Override
	protected boolean verify(String label, byte[] signedData, byte[] publicKey,
			byte[] signature) throws GeneralSecurityException {
		return crypto.verify(label, signedData, publicKey, signature);
	}
}

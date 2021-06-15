package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.api.crypto.AgreementPublicKey;
import org.briarproject.bramble.api.crypto.AuthenticatedCipher;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.briar.socialbackup.SocialBackupConstants;

import java.io.DataInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.inject.Provider;

public class ReturnShardTaskImpl {
	private final Provider<AuthenticatedCipher> cipherProvider;
	private final CryptoComponent crypto;
	private final SecureRandom secureRandom;
	final int NONCE_LENGTH = SocialBackupConstants.NONCE_BYTES;
	final int AUTH_TAG_BYTES = SocialBackupConstants.AUTH_TAG_BYTES;
	final int TIMEOUT = 120 * 1000; // TODO move to SocialBackupConstants
	final int AGREEMENT_PUBLIC_KEY_LENGTH = 32;
	SecretKey sharedSecret;
	final KeyPair localKeyPair;

	ReturnShardTaskImpl(Provider<AuthenticatedCipher> cipherProvider, CryptoComponent crypto) {
		this.cipherProvider = cipherProvider;
		this.crypto = crypto;
		this.secureRandom = crypto.getSecureRandom();
		localKeyPair = crypto.generateAgreementKeyPair();
	}

	byte[] generateNonce() {
		byte[] nonce = new byte[NONCE_LENGTH];
		secureRandom.nextBytes(nonce);
		return nonce;
	}

	void deriveSharedSecret(AgreementPublicKey remotePublicKey, byte[] context)
			throws
			GeneralSecurityException {
		sharedSecret =
				crypto.deriveSharedSecret("ShardReturn", remotePublicKey,
						localKeyPair, context);
	}

	byte[] encrypt(byte[] message, byte[] nonce)
			throws GeneralSecurityException {
		AuthenticatedCipher cipher = cipherProvider.get();
		cipher.init(true, sharedSecret, nonce);
		byte[] cipherText = new byte[message.length + cipher.getMacBytes()];
		cipher.process(message, 0, message.length, cipherText, 0);
		return cipherText;
	}

	byte[] decrypt(byte[] cipherText, byte[] nonce)
			throws GeneralSecurityException {
		AuthenticatedCipher cipher = cipherProvider.get();
		cipher.init(false, sharedSecret, nonce);
		byte[] message = new byte[cipherText.length - cipher.getMacBytes()];
		cipher.process(cipherText, 0, cipherText.length, message, 0);
		return message;
	}

	byte[] read(DataInputStream dis, int length)
			throws IOException {
		byte[] output = new byte[length];
		dis.readFully(output);
		return output;
	}
}

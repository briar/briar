package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.api.crypto.AgreementPublicKey;
import org.briarproject.bramble.api.crypto.AuthenticatedCipher;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.SecretKey;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

public class ReturnShardTaskImpl {
	private final AuthenticatedCipher cipher;
	private final CryptoComponent crypto;
	private final SecureRandom secureRandom;
	final int PORT = 3002;
	final int TIMEOUT = 120 * 1000;
	final int NONCE_LENGTH = 24; // TODO get these constants
	final int AGREEMENT_PUBLIC_KEY_LENGTH = 32;
	SecretKey sharedSecret;
	final KeyPair localKeyPair;

	ReturnShardTaskImpl(AuthenticatedCipher cipher, CryptoComponent crypto) {
		this.cipher = cipher;
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
		cipher.init(true, sharedSecret, nonce);
		byte[] cipherText = new byte[message.length + cipher.getMacBytes()];
		cipher.process(message, 0, message.length, cipherText, 0);
		return cipherText;
	}

	byte[] decrypt(byte[] cipherText, byte[] nonce)
			throws GeneralSecurityException {
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

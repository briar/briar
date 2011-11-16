package net.sf.briar.api.crypto;

import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.Mac;

public interface CryptoComponent {

	ErasableKey deriveFrameKey(byte[] secret, boolean initiator);

	ErasableKey deriveIvKey(byte[] secret, boolean initiator);

	ErasableKey deriveMacKey(byte[] secret, boolean initiator);

	byte[] deriveNextSecret(byte[] secret, int index, long connection);

	KeyPair generateKeyPair();

	ErasableKey generateTestKey();

	Cipher getFrameCipher();

	Cipher getIvCipher();

	KeyParser getKeyParser();

	Mac getMac();

	MessageDigest getMessageDigest();

	SecureRandom getSecureRandom();

	Signature getSignature();
}

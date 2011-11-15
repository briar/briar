package net.sf.briar.api.crypto;

import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.Mac;

public interface CryptoComponent {

	ErasableKey deriveFrameKey(byte[] source, boolean initiator);

	ErasableKey deriveIvKey(byte[] source, boolean initiator);

	ErasableKey deriveMacKey(byte[] source, boolean initiator);

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

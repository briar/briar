package net.sf.briar.api.crypto;

import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.Mac;

public interface CryptoComponent {

	ErasableKey deriveIncomingFrameKey(byte[] secret);

	ErasableKey deriveIncomingIvKey(byte[] secret);

	ErasableKey deriveIncomingMacKey(byte[] secret);

	ErasableKey deriveOutgoingFrameKey(byte[] secret);

	ErasableKey deriveOutgoingIvKey(byte[] secret);

	ErasableKey deriveOutgoingMacKey(byte[] secret);

	KeyPair generateKeyPair();

	Cipher getFrameCipher();

	Cipher getIvCipher();

	KeyParser getKeyParser();

	Mac getMac();

	MessageDigest getMessageDigest();

	SecureRandom getSecureRandom();

	Signature getSignature();

	ErasableKey generateTestKey();
}

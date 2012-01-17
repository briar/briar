package net.sf.briar.api.crypto;

import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.Mac;

public interface CryptoComponent {

	ErasableKey deriveSegmentKey(byte[] secret, boolean initiator);

	ErasableKey deriveTagKey(byte[] secret, boolean initiator);

	ErasableKey deriveMacKey(byte[] secret, boolean initiator);

	byte[] deriveNextSecret(byte[] secret, int index, long connection);

	KeyPair generateKeyPair();

	KeyParser getKeyParser();

	ErasableKey generateTestKey();

	MessageDigest getMessageDigest();

	SecureRandom getSecureRandom();

	Cipher getSegmentCipher();

	Signature getSignature();

	Cipher getTagCipher();

	Mac getMac();
}

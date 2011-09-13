package net.sf.briar.api.crypto;

import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

public interface CryptoComponent {

	SecretKey deriveIncomingFrameKey(byte[] secret);

	SecretKey deriveIncomingIvKey(byte[] secret);

	SecretKey deriveIncomingMacKey(byte[] secret);

	SecretKey deriveOutgoingFrameKey(byte[] secret);

	SecretKey deriveOutgoingIvKey(byte[] secret);

	SecretKey deriveOutgoingMacKey(byte[] secret);

	KeyPair generateKeyPair();

	SecretKey generateSecretKey();

	Cipher getFrameCipher();

	Cipher getIvCipher();

	KeyParser getKeyParser();

	Mac getMac();

	MessageDigest getMessageDigest();

	SecureRandom getSecureRandom();

	Signature getSignature();
}

package net.sf.briar.api.crypto;

import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

public interface CryptoComponent {

	SecretKey deriveIncomingMacKey(byte[] secret);

	SecretKey deriveIncomingFrameKey(byte[] secret);

	SecretKey deriveIncomingTagKey(byte[] secret);

	SecretKey deriveOutgoingMacKey(byte[] secret);

	SecretKey deriveOutgoingFrameKey(byte[] secret);

	SecretKey deriveOutgoingTagKey(byte[] secret);

	KeyPair generateKeyPair();

	SecretKey generateSecretKey();

	KeyParser getKeyParser();

	Mac getMac();

	MessageDigest getMessageDigest();

	Cipher getFrameCipher();

	Signature getSignature();

	Cipher getTagCipher();
}

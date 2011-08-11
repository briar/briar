package net.sf.briar.api.crypto;

import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

public interface CryptoComponent {

	SecretKey deriveMacKey(byte[] secret);

	SecretKey derivePacketKey(byte[] secret);

	SecretKey deriveTagKey(byte[] secret);

	KeyPair generateKeyPair();

	SecretKey generateSecretKey();

	KeyParser getKeyParser();

	Mac getMac();

	MessageDigest getMessageDigest();

	Cipher getPacketCipher();

	Signature getSignature();

	Cipher getTagCipher();
}

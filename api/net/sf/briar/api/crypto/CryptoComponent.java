package net.sf.briar.api.crypto;

import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

public interface CryptoComponent {

	SecretKey deriveMacKey(byte[] secret, boolean alice);

	SecretKey derivePacketKey(byte[] secret, boolean alice);

	SecretKey deriveTagKey(byte[] secret, boolean alice);

	KeyPair generateKeyPair();

	SecretKey generateSecretKey();

	KeyParser getKeyParser();

	Mac getMac();

	MessageDigest getMessageDigest();

	Cipher getPacketCipher();

	Signature getSignature();

	Cipher getTagCipher();
}

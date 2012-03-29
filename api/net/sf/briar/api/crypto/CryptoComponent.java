package net.sf.briar.api.crypto;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.Mac;

public interface CryptoComponent {

	ErasableKey deriveTagKey(byte[] secret, boolean initiator);

	ErasableKey deriveFrameKey(byte[] secret, boolean initiator);

	ErasableKey deriveMacKey(byte[] secret, boolean initiator);

	byte[][] deriveInitialSecrets(byte[] ourPublicKey, byte[] theirPublicKey,
			PrivateKey ourPrivateKey, int invitationCode, boolean initiator);

	int deriveConfirmationCode(byte[] secret);

	byte[] deriveNextSecret(byte[] secret, int index, long connection);

	KeyPair generateKeyPair();

	KeyParser getKeyParser();

	ErasableKey generateTestKey();

	MessageDigest getMessageDigest();

	PseudoRandom getPseudoRandom(int seed);

	SecureRandom getSecureRandom();

	Cipher getTagCipher();

	Cipher getFrameCipher();

	Signature getSignature();

	Mac getMac();
}

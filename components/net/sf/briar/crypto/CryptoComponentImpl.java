package net.sf.briar.crypto;

import java.io.UnsupportedEncodingException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.crypto.MessageDigest;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.google.inject.Inject;

class CryptoComponentImpl implements CryptoComponent {

	private static final String PROVIDER = "BC";
	private static final String DIGEST_ALGO = "SHA-256";
	private static final String KEY_PAIR_ALGO = "ECDSA";
	private static final int KEY_PAIR_BITS = 256;
	private static final String FRAME_CIPHER_ALGO = "AES/CTR/NoPadding";
	private static final String SECRET_KEY_ALGO = "AES";
	private static final int SECRET_KEY_BYTES = 32;
	private static final String IV_CIPHER_ALGO = "AES/ECB/NoPadding";
	private static final String MAC_ALGO = "HMacSHA256";
	private static final String SIGNATURE_ALGO = "ECDSA";

	private final KeyParser keyParser;
	private final KeyPairGenerator keyPairGenerator;

	@Inject
	CryptoComponentImpl() {
		Security.addProvider(new BouncyCastleProvider());
		try {
			keyParser = new KeyParserImpl(KEY_PAIR_ALGO, PROVIDER);
			keyPairGenerator = KeyPairGenerator.getInstance(KEY_PAIR_ALGO,
					PROVIDER);
			keyPairGenerator.initialize(KEY_PAIR_BITS);
		} catch(NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} catch(NoSuchProviderException e) {
			throw new RuntimeException(e);
		}
	}

	public ErasableKey deriveIncomingFrameKey(byte[] secret) {
		SharedSecret s = new SharedSecret(secret);
		return deriveFrameKey(s, !s.getAlice());
	}

	private ErasableKey deriveFrameKey(SharedSecret s, boolean alice) {
		if(alice) return deriveKey("F_A", s.getSecret());
		else return deriveKey("F_B", s.getSecret());
	}

	private ErasableKey deriveKey(String name, byte[] secret) {
		MessageDigest digest = getMessageDigest();
		assert digest.getDigestLength() == SECRET_KEY_BYTES;
		try {
			digest.update(name.getBytes("UTF-8"));
		} catch(UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		digest.update(secret);
		return new ErasableKeyImpl(digest.digest(), SECRET_KEY_ALGO);
	}

	public ErasableKey deriveIncomingIvKey(byte[] secret) {
		SharedSecret s = new SharedSecret(secret);
		return deriveIvKey(s, !s.getAlice());
	}

	private ErasableKey deriveIvKey(SharedSecret s, boolean alice) {
		if(alice) return deriveKey("I_A", s.getSecret());
		else return deriveKey("I_B", s.getSecret());
	}

	public ErasableKey deriveIncomingMacKey(byte[] secret) {
		SharedSecret s = new SharedSecret(secret);
		return deriveMacKey(s, !s.getAlice());
	}

	private ErasableKey deriveMacKey(SharedSecret s, boolean alice) {
		if(alice) return deriveKey("M_A", s.getSecret());
		else return deriveKey("M_B", s.getSecret());
	}

	public ErasableKey deriveOutgoingFrameKey(byte[] secret) {
		SharedSecret s = new SharedSecret(secret);
		return deriveFrameKey(s, s.getAlice());
	}

	public ErasableKey deriveOutgoingIvKey(byte[] secret) {
		SharedSecret s = new SharedSecret(secret);
		return deriveIvKey(s, s.getAlice());
	}

	public ErasableKey deriveOutgoingMacKey(byte[] secret) {
		SharedSecret s = new SharedSecret(secret);
		return deriveMacKey(s, s.getAlice());
	}

	public KeyPair generateKeyPair() {
		return keyPairGenerator.generateKeyPair();
	}

	public Cipher getFrameCipher() {
		try {
			return Cipher.getInstance(FRAME_CIPHER_ALGO, PROVIDER);
		} catch(NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} catch(NoSuchPaddingException e) {
			throw new RuntimeException(e);
		} catch(NoSuchProviderException e) {
			throw new RuntimeException(e);
		}
	}

	public Cipher getIvCipher() {
		try {
			return Cipher.getInstance(IV_CIPHER_ALGO, PROVIDER);
		} catch(NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} catch(NoSuchPaddingException e) {
			throw new RuntimeException(e);
		} catch(NoSuchProviderException e) {
			throw new RuntimeException(e);
		}
	}

	public KeyParser getKeyParser() {
		return keyParser;
	}

	public Mac getMac() {
		try {
			return Mac.getInstance(MAC_ALGO, PROVIDER);
		} catch(NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} catch(NoSuchProviderException e) {
			throw new RuntimeException(e);
		}
	}

	public MessageDigest getMessageDigest() {
		try {
			return new DoubleDigest(java.security.MessageDigest.getInstance(
					DIGEST_ALGO, PROVIDER));
		} catch(NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} catch(NoSuchProviderException e) {
			throw new RuntimeException(e);
		}
	}

	public SecureRandom getSecureRandom() {
		// FIXME: Implement a PRNG (pony/rainbow/nyancat generator)
		return new SecureRandom();
	}

	public Signature getSignature() {
		try {
			return Signature.getInstance(SIGNATURE_ALGO, PROVIDER);
		} catch(NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} catch(NoSuchProviderException e) {
			throw new RuntimeException(e);
		}
	}

	public ErasableKey generateTestKey() {
		byte[] b = new byte[SECRET_KEY_BYTES];
		getSecureRandom().nextBytes(b);
		return new ErasableKeyImpl(b, SECRET_KEY_ALGO);
	}
}

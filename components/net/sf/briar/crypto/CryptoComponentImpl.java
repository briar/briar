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

	public ErasableKey deriveFrameKey(byte[] source, boolean initiator) {
		if(initiator) return deriveKey("FRAME_I", source);
		else return deriveKey("FRAME_R", source);
	}

	public ErasableKey deriveIvKey(byte[] source, boolean initiator) {
		if(initiator) return deriveKey("IV_I", source);
		else return deriveKey("IV_R", source);
	}

	public ErasableKey deriveMacKey(byte[] source, boolean initiator) {
		if(initiator) return deriveKey("MAC_I", source);
		else return deriveKey("MAC_R", source);
	}

	private ErasableKey deriveKey(String name, byte[] source) {
		MessageDigest digest = getMessageDigest();
		assert digest.getDigestLength() == SECRET_KEY_BYTES;
		try {
			digest.update(name.getBytes("UTF-8"));
		} catch(UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		digest.update(source);
		return new ErasableKeyImpl(digest.digest(), SECRET_KEY_ALGO);
	}

	public KeyPair generateKeyPair() {
		return keyPairGenerator.generateKeyPair();
	}

	public ErasableKey generateTestKey() {
		byte[] b = new byte[SECRET_KEY_BYTES];
		getSecureRandom().nextBytes(b);
		return new ErasableKeyImpl(b, SECRET_KEY_ALGO);
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
}

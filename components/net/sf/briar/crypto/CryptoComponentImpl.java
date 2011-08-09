package net.sf.briar.crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.Signature;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyParser;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

class CryptoComponentImpl implements CryptoComponent {

	private static final String PROVIDER = "BC";
	private static final String DIGEST_ALGO = "SHA-256";
	private static final String KEY_PAIR_ALGO = "ECDSA";
	private static final int KEY_PAIR_KEYSIZE = 256; // Bits
	private static final String MAC_ALGO = "HMacSHA256";
	private static final String SECRET_KEY_ALGO = "AES";
	private static final int SECRET_KEY_KEYSIZE = 256; // Bits
	private static final String SIGNATURE_ALGO = "ECDSA";

	private final KeyParser keyParser;
	private final KeyPairGenerator keyPairGenerator;
	private final KeyGenerator keyGenerator;

	CryptoComponentImpl() {
		Security.addProvider(new BouncyCastleProvider());
		try {
			keyParser = new KeyParserImpl(KEY_PAIR_ALGO, PROVIDER);
			keyPairGenerator = KeyPairGenerator.getInstance(KEY_PAIR_ALGO,
					PROVIDER);
			keyPairGenerator.initialize(KEY_PAIR_KEYSIZE);
			keyGenerator = KeyGenerator.getInstance(SECRET_KEY_ALGO,
					PROVIDER);
			keyGenerator.init(SECRET_KEY_KEYSIZE);
		} catch(NoSuchAlgorithmException impossible) {
			throw new RuntimeException(impossible);
		} catch(NoSuchProviderException impossible) {
			throw new RuntimeException(impossible);
		}
	}

	public KeyPair generateKeyPair() {
		return keyPairGenerator.generateKeyPair();
	}

	public SecretKey generateSecretKey() {
		return keyGenerator.generateKey();
	}

	public KeyParser getKeyParser() {
		return keyParser;
	}

	public Mac getMac() {
		try {
			return Mac.getInstance(MAC_ALGO, PROVIDER);
		} catch(NoSuchAlgorithmException impossible) {
			throw new RuntimeException(impossible);
		} catch(NoSuchProviderException impossible) {
			throw new RuntimeException(impossible);
		}
	}

	public MessageDigest getMessageDigest() {
		try {
			return MessageDigest.getInstance(DIGEST_ALGO, PROVIDER);
		} catch(NoSuchAlgorithmException impossible) {
			throw new RuntimeException(impossible);
		} catch(NoSuchProviderException impossible) {
			throw new RuntimeException(impossible);
		}
	}

	public Signature getSignature() {
		try {
			return Signature.getInstance(SIGNATURE_ALGO, PROVIDER);
		} catch(NoSuchAlgorithmException impossible) {
			throw new RuntimeException(impossible);
		} catch(NoSuchProviderException impossible) {
			throw new RuntimeException(impossible);
		}
	}
}

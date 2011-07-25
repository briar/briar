package net.sf.briar.crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.Signature;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyParser;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

class CryptoComponentImpl implements CryptoComponent {

	private static final String PROVIDER = "BC";
	private static final String DIGEST_ALGO = "SHA-256";
	private static final String KEY_PAIR_ALGO = "ECDSA";
	private static final int KEY_PAIR_KEYSIZE = 256;
	private static final String SIGNATURE_ALGO = "ECDSA";

	private final KeyParser keyParser;
	private final KeyPairGenerator keyPairGenerator;

	CryptoComponentImpl() {
		Security.addProvider(new BouncyCastleProvider());
		try {
			keyParser = new KeyParserImpl(KEY_PAIR_ALGO, PROVIDER);
			keyPairGenerator = KeyPairGenerator.getInstance(KEY_PAIR_ALGO,
					PROVIDER);
			keyPairGenerator.initialize(KEY_PAIR_KEYSIZE);
		} catch(NoSuchAlgorithmException impossible) {
			throw new RuntimeException(impossible);
		} catch(NoSuchProviderException impossible) {
			throw new RuntimeException(impossible);
		}
	}

	public KeyPair generateKeyPair() {
		return keyPairGenerator.generateKeyPair();
	}

	public KeyParser getKeyParser() {
		return keyParser;
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

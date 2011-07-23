package net.sf.briar.crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;

import net.sf.briar.api.crypto.KeyParser;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class CryptoModule extends AbstractModule {

	private static final String DIGEST_ALGO = "SHA-256";
	private static final String KEY_PAIR_ALGO = "RSA";
	private static final String SIGNATURE_ALGO = "SHA256withRSA";

	@Override
	protected void configure() {
		try {
			bind(KeyParser.class).toInstance(new KeyParserImpl(KEY_PAIR_ALGO));
		} catch(NoSuchAlgorithmException e) {
			// FIXME: Can modules throw?
			throw new RuntimeException(e);
		}
	}

	@Provides
	MessageDigest getMessageDigest() {
		try {
			return MessageDigest.getInstance(DIGEST_ALGO);
		} catch(NoSuchAlgorithmException e) {
			// FIXME: Providers should not throw
			throw new RuntimeException(e);
		}
	}

	@Provides
	Signature getSignature() {
		try {
			return Signature.getInstance(SIGNATURE_ALGO);
		} catch(NoSuchAlgorithmException e) {
			// FIXME: Providers should not throw
			throw new RuntimeException(e);
		}
	}

	@Provides
	KeyPair generateKeyPair() {
		try {
			KeyPairGenerator gen = KeyPairGenerator.getInstance(KEY_PAIR_ALGO);
			return gen.generateKeyPair();
		} catch(NoSuchAlgorithmException e) {
			// FIXME: Providers should not throw
			throw new RuntimeException(e);
		}
	}
}

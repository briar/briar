package net.sf.briar.crypto;

import java.security.NoSuchAlgorithmException;

import net.sf.briar.api.crypto.KeyParser;

import com.google.inject.AbstractModule;

public class CryptoModule extends AbstractModule {

	public static final String DIGEST_ALGO = "SHA-256";
	public static final String KEY_PAIR_ALGO = "RSA";
	public static final String SIGNATURE_ALGO = "SHA256withRSA";

	@Override
	protected void configure() {
		try {
			bind(KeyParser.class).toInstance(new KeyParserImpl(KEY_PAIR_ALGO));
		} catch(NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}

package net.sf.briar.crypto;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import net.sf.briar.api.crypto.KeyParser;

class KeyParserImpl implements KeyParser {

	private final KeyFactory keyFactory;

	KeyParserImpl(String algorithm, String provider)
	throws NoSuchAlgorithmException, NoSuchProviderException {
		keyFactory = KeyFactory.getInstance(algorithm, provider);
	}

	public PublicKey parsePublicKey(byte[] encodedKey)
	throws InvalidKeySpecException {
		EncodedKeySpec e = new X509EncodedKeySpec(encodedKey);
		return keyFactory.generatePublic(e);
	}

}

package net.sf.briar.api.crypto;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

public interface KeyParser {

	PublicKey parsePublicKey(byte[] encodedKey) throws InvalidKeySpecException;

	PrivateKey parsePrivateKey(byte[] encodedKey)
			throws InvalidKeySpecException;
}

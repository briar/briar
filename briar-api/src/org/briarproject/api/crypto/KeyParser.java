package org.briarproject.api.crypto;

import java.security.GeneralSecurityException;

public interface KeyParser {

	PublicKey parsePublicKey(byte[] encodedKey) throws GeneralSecurityException;

	PrivateKey parsePrivateKey(byte[] encodedKey)
			throws GeneralSecurityException;
}

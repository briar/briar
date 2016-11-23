package org.briarproject.bramble.api.crypto;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.security.GeneralSecurityException;

@NotNullByDefault
public interface KeyParser {

	PublicKey parsePublicKey(byte[] encodedKey) throws GeneralSecurityException;

	PrivateKey parsePrivateKey(byte[] encodedKey)
			throws GeneralSecurityException;
}

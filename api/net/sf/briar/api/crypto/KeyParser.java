package net.sf.briar.api.crypto;

import java.security.GeneralSecurityException;
import java.security.PublicKey;

public interface KeyParser {

	PublicKey parsePublicKey(byte[] encodedKey) throws GeneralSecurityException;
}

package net.sf.briar.api.crypto;

import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;


public interface CryptoComponent {

	KeyPair generateKeyPair();

	KeyParser getKeyParser();

	MessageDigest getMessageDigest();

	Signature getSignature();
}

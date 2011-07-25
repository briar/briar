package net.sf.briar.api.protocol;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;

public interface MessageEncoder {

	/** Encodes an anonymous to an unrestricted group. */
	Message encodeMessage(MessageId parent, Group group, byte[] body)
	throws IOException, GeneralSecurityException;

	/** Encodes an anonymous message to a restricted group. */
	Message encodeMessage(MessageId parent, Group group, PrivateKey groupKey,
			byte[] body) throws IOException, GeneralSecurityException;

	/** Encodes a pseudonymous message to an unrestricted group. */
	Message encodeMessage(MessageId parent, Group group, Author author,
			PrivateKey authorKey, byte[] body) throws IOException,
			GeneralSecurityException;

	/** Encode a pseudonymous message to a restricted group. */
	Message encodeMessage(MessageId parent, Group group, PrivateKey groupKey,
			Author author, PrivateKey authorKey, byte[] body)
	throws IOException, GeneralSecurityException;
}

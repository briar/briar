package net.sf.briar.api.messaging;

import java.io.IOException;
import java.security.GeneralSecurityException;

import net.sf.briar.api.Author;
import net.sf.briar.api.crypto.PrivateKey;

public interface MessageFactory {

	/** Creates a private message. */
	Message createPrivateMessage(MessageId parent, String contentType,
			byte[] body) throws IOException, GeneralSecurityException;

	/** Creates an anonymous message to an unrestricted group. */
	Message createAnonymousMessage(MessageId parent, Group group,
			String contentType, byte[] body) throws IOException,
			GeneralSecurityException;

	/** Creates an anonymous message to a restricted group. */
	Message createAnonymousMessage(MessageId parent, Group group,
			PrivateKey groupKey, String contentType, byte[] body)
					throws IOException, GeneralSecurityException;

	/** Creates a pseudonymous message to an unrestricted group. */
	Message createPseudonymousMessage(MessageId parent, Group group,
			Author author, PrivateKey authorKey, String contentType,
			byte[] body) throws IOException, GeneralSecurityException;

	/** Creates a pseudonymous message to a restricted group. */
	Message createPseudonymousMessage(MessageId parent, Group group,
			PrivateKey groupKey, Author author, PrivateKey authorKey,
			String contentType, byte[] body) throws IOException,
			GeneralSecurityException;
}

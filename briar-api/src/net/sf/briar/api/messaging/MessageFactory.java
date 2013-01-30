package net.sf.briar.api.messaging;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;

public interface MessageFactory {

	/** Creates a private message. */
	Message createMessage(MessageId parent, String subject, byte[] body)
	throws IOException, GeneralSecurityException;

	/** Creates an anonymous message to an unrestricted group. */
	Message createMessage(MessageId parent, Group group, String subject,
			byte[] body) throws IOException, GeneralSecurityException;

	/** Creates an anonymous message to a restricted group. */
	Message createMessage(MessageId parent, Group group, PrivateKey groupKey,
			String subject, byte[] body) throws IOException,
			GeneralSecurityException;

	/** Creates a pseudonymous message to an unrestricted group. */
	Message createMessage(MessageId parent, Group group, Author author,
			PrivateKey authorKey, String subject, byte[] body)
	throws IOException, GeneralSecurityException;

	/** Creates a pseudonymous message to a restricted group. */
	Message createMessage(MessageId parent, Group group, PrivateKey groupKey,
			Author author, PrivateKey authorKey, String subject, byte[] body)
	throws IOException, GeneralSecurityException;
}

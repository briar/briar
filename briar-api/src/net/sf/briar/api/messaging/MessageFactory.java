package net.sf.briar.api.messaging;

import java.io.IOException;
import java.security.GeneralSecurityException;

import net.sf.briar.api.Author;
import net.sf.briar.api.crypto.PrivateKey;

public interface MessageFactory {

	/** Creates a private message. */
	Message createPrivateMessage(MessageId parent, String contentType,
			byte[] body) throws IOException, GeneralSecurityException;

	/** Creates an anonymous group message. */
	Message createAnonymousMessage(MessageId parent, Group group,
			String contentType, byte[] body) throws IOException,
			GeneralSecurityException;

	/** Creates a pseudonymous group message. */
	Message createPseudonymousMessage(MessageId parent, Group group,
			Author author, PrivateKey privateKey, String contentType,
			byte[] body) throws IOException, GeneralSecurityException;
}

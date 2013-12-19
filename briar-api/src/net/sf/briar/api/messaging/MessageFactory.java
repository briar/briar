package net.sf.briar.api.messaging;

import java.io.IOException;
import java.security.GeneralSecurityException;

import net.sf.briar.api.Author;
import net.sf.briar.api.crypto.PrivateKey;

public interface MessageFactory {

	Message createAnonymousMessage(MessageId parent, Group group,
			String contentType, long timestamp, byte[] body) throws IOException,
			GeneralSecurityException;

	Message createPseudonymousMessage(MessageId parent, Group group,
			Author author, PrivateKey privateKey, String contentType,
			long timestamp, byte[] body) throws IOException,
			GeneralSecurityException;
}

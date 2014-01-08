package org.briarproject.api.messaging;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.briarproject.api.Author;
import org.briarproject.api.crypto.PrivateKey;

public interface MessageFactory {

	Message createAnonymousMessage(MessageId parent, Group group,
			String contentType, long timestamp, byte[] body) throws IOException,
			GeneralSecurityException;

	Message createPseudonymousMessage(MessageId parent, Group group,
			Author author, PrivateKey privateKey, String contentType,
			long timestamp, byte[] body) throws IOException,
			GeneralSecurityException;
}

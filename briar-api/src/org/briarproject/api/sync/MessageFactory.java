package org.briarproject.api.sync;

import org.briarproject.api.Author;
import org.briarproject.api.crypto.PrivateKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public interface MessageFactory {

	Message createAnonymousMessage(MessageId parent, org.briarproject.api.sync.Group group,
			String contentType, long timestamp, byte[] body) throws IOException,
			GeneralSecurityException;

	Message createPseudonymousMessage(MessageId parent, org.briarproject.api.sync.Group group,
			Author author, PrivateKey privateKey, String contentType,
			long timestamp, byte[] body) throws IOException,
			GeneralSecurityException;
}

package org.briarproject.api.forum;

import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;

import java.io.IOException;
import java.security.GeneralSecurityException;

public interface ForumPostFactory {

	Message createAnonymousPost(MessageId parent, Forum forum,
			String contentType, long timestamp, byte[] body) throws IOException,
			GeneralSecurityException;

	Message createPseudonymousPost(MessageId parent, Forum forum,
			Author author, PrivateKey privateKey, String contentType,
			long timestamp, byte[] body) throws IOException,
			GeneralSecurityException;
}

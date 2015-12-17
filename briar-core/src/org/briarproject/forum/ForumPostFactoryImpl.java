package org.briarproject.forum;

import com.google.inject.Inject;

import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.MessageId;

import java.io.IOException;
import java.security.GeneralSecurityException;

// Temporary facade during sync protocol refactoring
class ForumPostFactoryImpl implements ForumPostFactory {

	private final MessageFactory messageFactory;

	@Inject
	ForumPostFactoryImpl(MessageFactory messageFactory) {
		this.messageFactory = messageFactory;
	}

	@Override
	public Message createAnonymousPost(MessageId parent, Forum forum,
			String contentType, long timestamp, byte[] body)
			throws IOException, GeneralSecurityException {
		return messageFactory.createAnonymousMessage(parent,
				((ForumImpl) forum).getGroup(), contentType, timestamp, body);
	}

	@Override
	public Message createPseudonymousPost(MessageId parent, Forum forum,
			Author author, PrivateKey privateKey, String contentType,
			long timestamp, byte[] body)
			throws IOException, GeneralSecurityException {
		return messageFactory.createPseudonymousMessage(parent,
				((ForumImpl) forum).getGroup(), author, privateKey, contentType,
				timestamp, body);
	}
}

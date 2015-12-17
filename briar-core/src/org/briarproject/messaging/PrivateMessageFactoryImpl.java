package org.briarproject.messaging;

import com.google.inject.Inject;

import org.briarproject.api.messaging.PrivateConversation;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.MessageId;

import java.io.IOException;
import java.security.GeneralSecurityException;

// Temporary facade during sync protocol refactoring
class PrivateMessageFactoryImpl implements PrivateMessageFactory {

	private final MessageFactory messageFactory;

	@Inject
	PrivateMessageFactoryImpl(MessageFactory messageFactory) {
		this.messageFactory = messageFactory;
	}

	@Override
	public Message createPrivateMessage(MessageId parent,
			PrivateConversation conversation, String contentType,
			long timestamp, byte[] body)
			throws IOException, GeneralSecurityException {
		return messageFactory.createAnonymousMessage(parent,
				((PrivateConversationImpl) conversation).getGroup(),
				contentType, timestamp, body);
	}
}

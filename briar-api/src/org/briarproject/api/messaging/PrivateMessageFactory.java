package org.briarproject.api.messaging;

import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;

import java.io.IOException;
import java.security.GeneralSecurityException;

public interface PrivateMessageFactory {

	Message createPrivateMessage(MessageId parent,
			PrivateConversation conversation, String contentType,
			long timestamp, byte[] body) throws IOException,
			GeneralSecurityException;
}

package org.briarproject.api.messaging;

import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.io.IOException;
import java.security.GeneralSecurityException;

public interface PrivateMessageFactory {

	PrivateMessage createPrivateMessage(GroupId groupId, long timestamp,
			MessageId parent, String contentType, byte[] body)
			throws IOException, GeneralSecurityException;
}

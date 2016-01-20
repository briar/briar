package org.briarproject.api.sync;

import java.io.IOException;

public interface MessageFactory {

	Message createMessage(GroupId groupId, long timestamp, byte[] body)
			throws IOException;
}

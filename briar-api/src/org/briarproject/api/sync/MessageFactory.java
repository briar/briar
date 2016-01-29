package org.briarproject.api.sync;

public interface MessageFactory {

	Message createMessage(GroupId groupId, long timestamp, byte[] body);
}

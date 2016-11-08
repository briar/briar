package org.briarproject.api.sync;

public interface MessageFactory {

	Message createMessage(GroupId g, long timestamp, byte[] body);

	Message createMessage(MessageId m, byte[] raw);
}

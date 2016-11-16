package org.briarproject.api.sync;

import org.briarproject.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface MessageFactory {

	Message createMessage(GroupId g, long timestamp, byte[] body);

	Message createMessage(MessageId m, byte[] raw);
}

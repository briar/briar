package org.briarproject.bramble.api.sync;

import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface MessageFactory {

	Message createMessage(GroupId g, long timestamp, byte[] body);

	Message createMessage(byte[] raw);

	byte[] getRawMessage(Message m);
}

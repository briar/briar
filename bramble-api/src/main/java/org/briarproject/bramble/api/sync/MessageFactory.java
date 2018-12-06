package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.tree.TreeHash;

@NotNullByDefault
public interface MessageFactory {

	Message createMessage(GroupId g, long timestamp, byte[] body);

	Message createMessage(byte[] raw);

	byte[] getRawMessage(Message m);

	MessageId getMessageId(GroupId g, long timestamp, TreeHash rootHash);
}

package org.briarproject.bramble.api.sync;

import java.util.Collection;

/**
 * An interface for holding the IDs of messages sent and acked during an
 * outgoing {@link SyncSession} so they can be recorded in the DB as sent
 * or acked at some later time.
 */
public interface DeferredSendHandler {

	void onAckSent(Collection<MessageId> acked);

	void onMessageSent(MessageId sent);
}

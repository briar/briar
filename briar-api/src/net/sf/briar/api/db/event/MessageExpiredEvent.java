package net.sf.briar.api.db.event;

import java.util.Collection;

import net.sf.briar.api.messaging.MessageId;

/**
 * An event that is broadcast when one or messages expire from the database,
 * potentially changing the database's retention time.
 */
public class MessageExpiredEvent extends DatabaseEvent {

	private final Collection<MessageId> expired;

	public MessageExpiredEvent(Collection<MessageId> expired) {
		this.expired = expired;
	}

	public Collection<MessageId> getMessageIds() {
		return expired;
	}
}

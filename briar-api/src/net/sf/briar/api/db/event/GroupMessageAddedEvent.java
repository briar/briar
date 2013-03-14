package net.sf.briar.api.db.event;

import net.sf.briar.api.messaging.Message;

/** An event that is broadcast when a group message is added to the database. */
public class GroupMessageAddedEvent extends DatabaseEvent {

	private final Message message;
	private final boolean incoming;

	public GroupMessageAddedEvent(Message message, boolean incoming) {
		this.message = message;
		this.incoming = incoming;
	}

	public Message getMessage() {
		return message;
	}

	public boolean isIncoming() {
		return incoming;
	}
}

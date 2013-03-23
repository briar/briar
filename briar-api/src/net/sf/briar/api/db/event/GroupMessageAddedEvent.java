package net.sf.briar.api.db.event;

import net.sf.briar.api.messaging.Group;

/** An event that is broadcast when a group message is added to the database. */
public class GroupMessageAddedEvent extends DatabaseEvent {

	private final Group group;
	private final boolean incoming;

	public GroupMessageAddedEvent(Group group, boolean incoming) {
		this.group = group;
		this.incoming = incoming;
	}

	public Group getGroup() {
		return group;
	}

	public boolean isIncoming() {
		return incoming;
	}
}

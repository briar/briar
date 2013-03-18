package net.sf.briar.api.db.event;

import net.sf.briar.api.messaging.GroupId;

/** An event that is broadcast when a group message is added to the database. */
public class GroupMessageAddedEvent extends DatabaseEvent {

	private final GroupId groupId;
	private final boolean incoming;

	public GroupMessageAddedEvent(GroupId groupId, boolean incoming) {
		this.groupId = groupId;
		this.incoming = incoming;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public boolean isIncoming() {
		return incoming;
	}
}

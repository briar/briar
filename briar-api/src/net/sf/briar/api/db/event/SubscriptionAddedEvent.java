package net.sf.briar.api.db.event;

import net.sf.briar.api.messaging.GroupId;

/** An event that is broadcast when the user subscribes to a group. */
public class SubscriptionAddedEvent extends DatabaseEvent {

	private final GroupId groupId;

	public SubscriptionAddedEvent(GroupId groupId) {
		this.groupId = groupId;
	}

	public GroupId getGroupId() {
		return groupId;
	}
}

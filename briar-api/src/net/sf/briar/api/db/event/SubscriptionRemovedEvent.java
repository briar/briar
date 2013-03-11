package net.sf.briar.api.db.event;

import net.sf.briar.api.messaging.GroupId;

/** An event that is broadcast when the user unsubscribes from a group. */
public class SubscriptionRemovedEvent extends DatabaseEvent {

	private final GroupId groupId;

	public SubscriptionRemovedEvent(GroupId groupId) {
		this.groupId = groupId;
	}

	public GroupId getGroupId() {
		return groupId;
	}
}

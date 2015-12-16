package org.briarproject.api.event;

import org.briarproject.api.sync.Group;

/** An event that is broadcast when the user subscribes to a group. */
public class SubscriptionAddedEvent extends Event {

	private final Group group;

	public SubscriptionAddedEvent(Group group) {
		this.group = group;
	}

	public Group getGroup() {
		return group;
	}
}

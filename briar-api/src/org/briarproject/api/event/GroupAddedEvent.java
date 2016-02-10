package org.briarproject.api.event;

import org.briarproject.api.sync.Group;

/** An event that is broadcast when a group is added. */
public class GroupAddedEvent extends Event {

	private final Group group;

	public GroupAddedEvent(Group group) {
		this.group = group;
	}

	public Group getGroup() {
		return group;
	}
}

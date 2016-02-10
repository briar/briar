package org.briarproject.api.event;

import org.briarproject.api.sync.Group;

/** An event that is broadcast when a group is removed. */
public class GroupRemovedEvent extends Event {

	private final Group group;

	public GroupRemovedEvent(Group group) {
		this.group = group;
	}

	public Group getGroup() {
		return group;
	}
}

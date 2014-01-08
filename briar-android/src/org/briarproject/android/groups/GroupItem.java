package org.briarproject.android.groups;

import org.briarproject.api.messaging.Group;

class GroupItem {

	static final GroupItem NEW = new GroupItem(null);

	private final Group group;

	GroupItem(Group group) {
		this.group = group;
	}

	Group getGroup() {
		return group;
	}
}

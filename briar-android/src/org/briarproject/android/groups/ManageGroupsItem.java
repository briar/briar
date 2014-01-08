package org.briarproject.android.groups;

import org.briarproject.api.messaging.GroupStatus;

class ManageGroupsItem {

	static final ManageGroupsItem NONE = new ManageGroupsItem(null);

	private final GroupStatus status;

	ManageGroupsItem(GroupStatus status) {
		this.status = status;
	}

	GroupStatus getGroupStatus() {
		return status;
	}
}

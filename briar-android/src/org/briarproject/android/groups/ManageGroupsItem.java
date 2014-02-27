package org.briarproject.android.groups;

import org.briarproject.api.messaging.GroupStatus;

class ManageGroupsItem {

	private final GroupStatus status;

	ManageGroupsItem(GroupStatus status) {
		this.status = status;
	}

	GroupStatus getGroupStatus() {
		return status;
	}
}

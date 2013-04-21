package net.sf.briar.android.groups;

import net.sf.briar.api.messaging.GroupStatus;

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

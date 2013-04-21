package net.sf.briar.android.blogs;

import net.sf.briar.api.messaging.GroupStatus;

class ManageBlogsItem {

	static final ManageBlogsItem NONE = new ManageBlogsItem(null);

	private final GroupStatus status;

	ManageBlogsItem(GroupStatus status) {
		this.status = status;
	}

	GroupStatus getGroupStatus() {
		return status;
	}
}

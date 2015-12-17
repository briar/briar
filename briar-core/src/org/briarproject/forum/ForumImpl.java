package org.briarproject.forum;

import org.briarproject.api.forum.Forum;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;

// Temporary facade during sync protocol refactoring
class ForumImpl implements Forum {

	private final Group group;

	ForumImpl(Group group) {
		this.group = group;
	}

	public GroupId getId() {
		return group.getId();
	}

	public String getName() {
		return group.getName();
	}

	Group getGroup() {
		return group;
	}

	@Override
	public int hashCode() {
		return group.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ForumImpl && group.equals(((ForumImpl) o).group);
	}
}

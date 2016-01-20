package org.briarproject.api.messaging;

import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;

// TODO: Remove if no longer needed
public class PrivateConversation {

	private final Group group;

	public PrivateConversation(Group group) {
		this.group = group;
	}

	public GroupId getId() {
		return group.getId();
	}

	public Group getGroup() {
		return group;
	}

	@Override
	public int hashCode() {
		return group.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof PrivateConversation
				&& group.equals(((PrivateConversation) o).group);
	}
}

package org.briarproject.messaging;

import org.briarproject.api.messaging.PrivateConversation;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;

// Temporary facade during sync protocol refactoring
class PrivateConversationImpl implements PrivateConversation {

	private final Group group;

	PrivateConversationImpl(Group group) {
		this.group = group;
	}

	@Override
	public GroupId getId() {
		return group.getId();
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
		return o instanceof PrivateConversationImpl
				&& group.equals(((PrivateConversationImpl) o).group);
	}
}

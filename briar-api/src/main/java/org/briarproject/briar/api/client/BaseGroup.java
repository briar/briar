package org.briarproject.briar.api.client;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public abstract class BaseGroup {

	private final Group group;

	public BaseGroup(Group group) {
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
		return o instanceof BaseGroup &&
				getGroup().equals(((BaseGroup) o).getGroup());
	}

}

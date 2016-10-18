package org.briarproject.api.clients;

import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
public abstract class BaseGroup {

	private final Group group;
	private final String name;

	public BaseGroup(Group group, String name) {
		this.group = group;
		this.name = name;
	}

	@NotNull
	public GroupId getId() {
		return group.getId();
	}

	@NotNull
	public Group getGroup() {
		return group;
	}

	@NotNull
	public String getName() {
		return name;
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

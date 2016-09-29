package org.briarproject.api.clients;

import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.jetbrains.annotations.NotNull;

public abstract class BaseGroup {

	private final Group group;
	private final String name;
	private final byte[] salt;

	public BaseGroup(@NotNull Group group, @NotNull String name, byte[] salt) {
		this.group = group;
		this.name = name;
		this.salt = salt;
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

	public byte[] getSalt() {
		return salt;
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

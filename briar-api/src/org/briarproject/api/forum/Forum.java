package org.briarproject.api.forum;

import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;

public class Forum {

	private final Group group;
	private final String name;
	private final byte[] salt;

	public Forum(Group group, String name, byte[] salt) {
		this.group = group;
		this.name = name;
		this.salt = salt;
	}

	public GroupId getId() {
		return group.getId();
	}

	public Group getGroup() {
		return group;
	}

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
		return o instanceof Forum && group.equals(((Forum) o).group);
	}
}

package net.sf.briar.protocol;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;

class GroupImpl implements Group {

	private final GroupId id;
	private final String name;
	private final byte[] publicKey;

	GroupImpl(GroupId id, String name, byte[] publicKey) {
		this.id = id;
		this.name = name;
		this.publicKey = publicKey;
	}

	public GroupId getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public byte[] getPublicKey() {
		return publicKey;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Group && id.equals(((Group) o).getId());
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}

package net.sf.briar.protocol;

import java.security.PublicKey;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;

public class GroupImpl implements Group {

	private final GroupId id;
	private final String name;
	private final byte[] salt;
	private final PublicKey publicKey;

	GroupImpl(GroupId id, String name, byte[] salt, PublicKey publicKey) {
		assert salt == null || publicKey == null;
		this.id = id;
		this.name = name;
		this.salt = salt;
		this.publicKey = publicKey;
	}

	public GroupId getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public boolean isRestricted() {
		return salt == null;
	}

	public byte[] getSalt() {
		return salt;
	}

	public PublicKey getPublicKey() {
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

package net.sf.briar.api.messaging;

/** A group to which users may subscribe. */
public class Group {

	private final GroupId id;
	private final String name;
	private final byte[] salt;
	private final boolean isPrivate;

	public Group(GroupId id, String name, byte[] salt, boolean isPrivate) {
		this.id = id;
		this.name = name;
		this.salt = salt;
		this.isPrivate = isPrivate;
	}

	/** Returns the group's unique identifier. */
	public GroupId getId() {
		return id;
	}

	/** Returns the group's name. */
	public String getName() {
		return name;
	}

	/**
	 * Returns the salt used to distinguish the group from other groups with
	 * the same name.
	 */
	public byte[] getSalt() {
		return salt;
	}

	/** Returns true if the group is private. */
	public boolean isPrivate() {
		return isPrivate;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Group && id.equals(((Group) o).id);
	}
}

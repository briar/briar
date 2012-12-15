package net.sf.briar.api.protocol;

/** A group to which users may subscribe. */
public class Group {

	private final GroupId id;
	private final String name;
	private final byte[] publicKey;

	public Group(GroupId id, String name, byte[] publicKey) {
		this.id = id;
		this.name = name;
		this.publicKey = publicKey;
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
	 * If the group is restricted, returns the public key that is used to
	 * authorise all messages sent to the group. Otherwise returns null.
	 */
	public byte[] getPublicKey() {
		return publicKey;
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

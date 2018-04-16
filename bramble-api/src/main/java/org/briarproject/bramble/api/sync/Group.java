package org.briarproject.bramble.api.sync;

import static org.briarproject.bramble.api.sync.SyncConstants.MAX_GROUP_DESCRIPTOR_LENGTH;

public class Group {

	public enum Visibility {

		INVISIBLE(0), // The group is not visible
		VISIBLE(1), // The group is visible, messages are accepted but not sent
		SHARED(2); // The group is visible, messages are accepted and sent

		private final int value;

		Visibility(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}

		public static Visibility min(Visibility a, Visibility b) {
			return a.getValue() < b.getValue() ? a : b;
		}
	}

	/**
	 * The current version of the group format.
	 */
	public static final int FORMAT_VERSION = 1;

	private final GroupId id;
	private final ClientId clientId;
	private final int majorVersion;
	private final byte[] descriptor;

	public Group(GroupId id, ClientId clientId, int majorVersion,
			byte[] descriptor) {
		if (descriptor.length > MAX_GROUP_DESCRIPTOR_LENGTH)
			throw new IllegalArgumentException();
		this.id = id;
		this.clientId = clientId;
		this.majorVersion = majorVersion;
		this.descriptor = descriptor;
	}

	/**
	 * Returns the group's unique identifier.
	 */
	public GroupId getId() {
		return id;
	}

	/**
	 * Returns the ID of the client to which the group belongs.
	 */
	public ClientId getClientId() {
		return clientId;
	}

	/**
	 * Returns the major version of the client to which the group belongs.
	 */
	public int getMajorVersion() {
		return majorVersion;
	}

	/**
	 * Returns the group's descriptor.
	 */
	public byte[] getDescriptor() {
		return descriptor;
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

package org.briarproject.api.sync;

import static org.briarproject.api.sync.SyncConstants.MAX_GROUP_DESCRIPTOR_LENGTH;

/** A group to which users may subscribe. */
public class Group {

	private final GroupId id;
	private final ClientId clientId;
	private final byte[] descriptor;

	public Group(GroupId id, ClientId clientId, byte[] descriptor) {
		if (descriptor.length > MAX_GROUP_DESCRIPTOR_LENGTH)
			throw new IllegalArgumentException();
		this.id = id;
		this.clientId = clientId;
		this.descriptor = descriptor;
	}

	/** Returns the group's unique identifier. */
	public GroupId getId() {
		return id;
	}

	/** Returns the ID of the client to which the group belongs. */
	public ClientId getClientId() {
		return clientId;
	}

	/** Returns the group's descriptor. */
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

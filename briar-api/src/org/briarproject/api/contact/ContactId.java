package org.briarproject.api.contact;

/**
 * Type-safe wrapper for an integer that uniquely identifies a contact within
 * the scope of a single node.
 */
public class ContactId {

	private final int id;

	public ContactId(int id) {
		this.id = id;
	}

	public int getInt() {
		return id;
	}

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ContactId && id == ((ContactId) o).id;
	}
}

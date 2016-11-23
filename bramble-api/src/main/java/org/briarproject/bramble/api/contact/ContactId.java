package org.briarproject.bramble.api.contact;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * Type-safe wrapper for an integer that uniquely identifies a contact within
 * the scope of a single node.
 */
@Immutable
@NotNullByDefault
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

package org.briarproject.api;

import java.util.Arrays;

public abstract class UniqueId {

	/** The length of a unique identifier in bytes. */
	public static final int LENGTH = 32;

	protected final byte[] id;

	private int hashCode = -1;

	protected UniqueId(byte[] id) {
		if (id.length != LENGTH) throw new IllegalArgumentException();
		this.id = id;
	}

	public byte[] getBytes() {
		return id;
	}

	@Override
	public int hashCode() {
		// Thread-safe because if two or more threads check and update the
		// value, they'll calculate the same value
		if (hashCode == -1) hashCode = Arrays.hashCode(id);
		return hashCode;
	}
}

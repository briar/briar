package org.briarproject.api;

import java.util.Arrays;
import java.util.Comparator;

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

	public static class IdComparator implements Comparator<UniqueId> {

		public static final IdComparator INSTANCE = new IdComparator();

		@Override
		public int compare(UniqueId a, UniqueId b) {
			byte[] aBytes = a.getBytes(), bBytes = b.getBytes();
			for (int i = 0; i < UniqueId.LENGTH; i++) {
				int aUnsigned = aBytes[i] & 0xFF, bUnsigned = bBytes[i] & 0xFF;
				if (aUnsigned < bUnsigned) return -1;
				if (aUnsigned > bUnsigned) return 1;
			}
			return 0;
		}
	}
}

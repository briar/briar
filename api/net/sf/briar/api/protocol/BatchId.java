package net.sf.briar.api.protocol;

import java.util.Arrays;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a batch of
 * messages.
 */
public class BatchId {

	public static final int LENGTH = 32;

	private final byte[] id;

	public BatchId(byte[] id) {
		assert id.length == LENGTH;
		this.id = id;
	}

	public byte[] getBytes() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof BatchId)
			return Arrays.equals(id, ((BatchId) o).id);
		return false;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(id);
	}
}

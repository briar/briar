package net.sf.briar.api.protocol;

import java.util.Arrays;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a batch of
 * messages.
 */
public class BatchId extends UniqueId {

	public BatchId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof BatchId)
			return Arrays.equals(id, ((BatchId) o).id);
		return false;
	}
}

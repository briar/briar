package net.sf.briar.api.protocol;

import java.util.Arrays;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a bundle of
 * acknowledgements, subscriptions, and batches of messages.
 */
public class BundleId {

	public static final BundleId NONE = new BundleId(new byte[] {
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
	});

	public static final int LENGTH = 32;

	private final byte[] id;

	public BundleId(byte[] id) {
		assert id.length == LENGTH;
		this.id = id;
	}

	public byte[] getBytes() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof BundleId)
			return Arrays.equals(id, ((BundleId) o).id);
		return false;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(id);
	}
}

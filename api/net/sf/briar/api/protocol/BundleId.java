package net.sf.briar.api.protocol;

import java.util.Arrays;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a bundle of
 * acknowledgements, subscriptions, and batches of messages.
 */
public class BundleId extends UniqueId {

	public static final BundleId NONE = new BundleId(new byte[] {
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
	});

	public BundleId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof BundleId)
			return Arrays.equals(id, ((BundleId) o).id);
		return false;
	}
}

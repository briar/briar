package org.briarproject.bramble.api;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.StringUtils;

import java.util.Arrays;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A wrapper around a byte array, to allow it to be stored in maps etc.
 */
@ThreadSafe
@NotNullByDefault
public class Bytes implements Comparable<Bytes> {

	private final byte[] bytes;

	private int hashCode = -1;

	public Bytes(byte[] bytes) {
		this.bytes = bytes;
	}

	public byte[] getBytes() {
		return bytes;
	}

	@Override
	public int hashCode() {
		// Thread-safe because if two or more threads check and update the
		// value, they'll calculate the same value
		if (hashCode == -1) hashCode = Arrays.hashCode(bytes);
		return hashCode;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		return o instanceof Bytes && Arrays.equals(bytes, ((Bytes) o).bytes);
	}

	@Override
	public int compareTo(Bytes other) {
		return compare(bytes, other.bytes);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() +
				"(" + StringUtils.toHexString(getBytes()) + ")";
	}

	public static int compare(byte[] a, byte[] b) {
		int length = Math.min(a.length, b.length);
		for (int i = 0; i < length; i++) {
			int aUnsigned = a[i] & 0xFF, bUnsigned = b[i] & 0xFF;
			if (aUnsigned < bUnsigned) return -1;
			if (aUnsigned > bUnsigned) return 1;
		}
		return a.length - b.length;
	}
}

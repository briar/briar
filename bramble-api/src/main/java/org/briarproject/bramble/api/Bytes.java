package org.briarproject.bramble.api;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.StringUtils;

import java.util.Arrays;
import java.util.Comparator;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A wrapper around a byte array, to allow it to be stored in maps etc.
 */
@ThreadSafe
@NotNullByDefault
public class Bytes implements Comparable<Bytes> {

	public static final BytesComparator COMPARATOR = new BytesComparator();

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
	public boolean equals(Object o) {
		return o instanceof Bytes && Arrays.equals(bytes, ((Bytes) o).bytes);
	}

	@Override
	public int compareTo(Bytes other) {
		byte[] aBytes = bytes, bBytes = other.bytes;
		int length = Math.min(aBytes.length, bBytes.length);
		for (int i = 0; i < length; i++) {
			int aUnsigned = aBytes[i] & 0xFF, bUnsigned = bBytes[i] & 0xFF;
			if (aUnsigned < bUnsigned) return -1;
			if (aUnsigned > bUnsigned) return 1;
		}
		return aBytes.length - bBytes.length;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() +
				"(" + StringUtils.toHexString(getBytes()) + ")";
	}

	public static class BytesComparator implements Comparator<Bytes> {

		@Override
		public int compare(Bytes a, Bytes b) {
			return a.compareTo(b);
		}
	}
}

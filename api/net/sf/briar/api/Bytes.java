package net.sf.briar.api;

import java.util.Arrays;

/** A wrapper around a byte array, to allow it to be stored in maps etc. */
public class Bytes {

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
		if(hashCode == -1) hashCode = Arrays.hashCode(bytes);
		return hashCode;
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof Bytes)
			return Arrays.equals(bytes, ((Bytes) o).bytes);
		return false;
	}
}

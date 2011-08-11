package net.sf.briar.api;

import java.util.Arrays;

/** A wrapper around a byte array, to allow it to be stored in maps etc. */
public class Bytes {

	private final byte[] bytes;

	public Bytes(byte[] bytes) {
		this.bytes = bytes;
	}

	public byte[] getBytes() {
		return bytes;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(bytes);
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof Bytes)
			return Arrays.equals(bytes, ((Bytes) o).bytes);
		return false;
	}
}

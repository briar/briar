package net.sf.briar.api.serial;

import java.util.Arrays;


/** A byte array wrapped in the Raw interface. */
public class RawByteArray implements Raw {

	private final byte[] bytes;

	public RawByteArray(byte[] bytes) {
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
		if(o instanceof Raw) return Arrays.equals(bytes, ((Raw) o).getBytes());
		return false;
	}
}

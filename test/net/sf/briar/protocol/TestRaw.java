package net.sf.briar.protocol;

import java.util.Arrays;

import net.sf.briar.api.serial.Raw;

class TestRaw implements Raw {

	private final byte[] bytes;

	TestRaw(byte[] bytes) {
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

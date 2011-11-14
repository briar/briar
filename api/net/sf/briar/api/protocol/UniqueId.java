package net.sf.briar.api.protocol;

import java.util.Arrays;

public abstract class UniqueId {

	/** The length of a unique identifier in bytes. */
	public static final int LENGTH = 32;

	protected final byte[] id;

	protected UniqueId(byte[] id) {
		assert id.length == LENGTH;
		this.id = id;
	}

	public byte[] getBytes() {
		return id;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(id);
	}
}

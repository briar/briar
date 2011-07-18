package net.sf.briar.api.protocol;

import java.util.Arrays;

import net.sf.briar.api.serial.Raw;
import net.sf.briar.api.serial.Writable;

public abstract class UniqueId implements Raw, Writable {

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

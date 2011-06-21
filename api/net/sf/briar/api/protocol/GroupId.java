package net.sf.briar.api.protocol;

import java.util.Arrays;

public class GroupId {

	public static final int LENGTH = 32;

	private final byte[] id;

	public GroupId(byte[] id) {
		assert id.length == LENGTH;
		this.id = id;
	}

	public byte[] getBytes() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof GroupId)
			return Arrays.equals(id, ((GroupId) o).id);
		return false;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(id);
	}
}

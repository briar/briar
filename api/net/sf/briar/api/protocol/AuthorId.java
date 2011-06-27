package net.sf.briar.api.protocol;

import java.util.Arrays;

/** Uniquely identifies a pseudonymous author. */
public class AuthorId {

	public static final int LENGTH = 32;

	// FIXME: Replace this with an isSelf() method that compares an AuthorId
	// to any and all local AuthorIds.
	public static final AuthorId SELF = new AuthorId(new byte[] {
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
			16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31
	});

	private final byte[] id;

	public AuthorId(byte[] id) {
		assert id.length == LENGTH;
		this.id = id;
	}

	public byte[] getBytes() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof AuthorId)
			return Arrays.equals(id, ((AuthorId) o).id);
		return false;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(id);
	}
}

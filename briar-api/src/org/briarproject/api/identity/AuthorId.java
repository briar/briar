package org.briarproject.api.identity;

import org.briarproject.api.UniqueId;

import java.util.Arrays;

/**
 * Type-safe wrapper for a byte array that uniquely identifies an
 * {@link Author}.
 */
public class AuthorId extends UniqueId {

	public AuthorId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof AuthorId)
			return Arrays.equals(id, ((AuthorId) o).id);
		return false;
	}
}

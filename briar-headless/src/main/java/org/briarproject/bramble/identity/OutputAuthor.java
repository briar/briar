package org.briarproject.bramble.identity;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
@SuppressWarnings("WeakerAccess")
public class OutputAuthor {

	public final byte[] id;
	public final String name;
	public final byte[] publicKey;

	public OutputAuthor(Author author) {
		this.id = author.getId().getBytes();
		this.name = author.getName();
		this.publicKey = author.getPublicKey();
	}

}

package net.sf.briar.protocol;

import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorId;

class AuthorImpl implements Author {

	private final AuthorId id;
	private final String name;
	private final byte[] publicKey;

	AuthorImpl(AuthorId id, String name, byte[] publicKey) {
		this.id = id;
		this.name = name;
		this.publicKey = publicKey;
	}

	public AuthorId getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public byte[] getPublicKey() {
		return publicKey;
	}
}

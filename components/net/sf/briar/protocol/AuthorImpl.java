package net.sf.briar.protocol;

import java.io.IOException;

import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.serial.Writer;

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

	public void writeTo(Writer w) throws IOException {
		w.writeUserDefinedTag(Types.AUTHOR);
		w.writeString(name);
		w.writeBytes(publicKey);
	}
}

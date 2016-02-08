package org.briarproject.identity;

import org.briarproject.api.FormatException;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;

import java.io.IOException;

import static org.briarproject.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;

class AuthorReader implements ObjectReader<Author> {

	private final AuthorFactory authorFactory;

	AuthorReader(AuthorFactory authorFactory) {
		this.authorFactory = authorFactory;
	}

	public Author readObject(BdfReader r) throws IOException {
		r.readListStart();
		String name = r.readString(MAX_AUTHOR_NAME_LENGTH);
		if (name.length() == 0) throw new FormatException();
		byte[] publicKey = r.readRaw(MAX_PUBLIC_KEY_LENGTH);
		r.readListEnd();
		return authorFactory.createAuthor(name, publicKey);
	}
}

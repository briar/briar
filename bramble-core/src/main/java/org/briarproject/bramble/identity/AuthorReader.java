package org.briarproject.bramble.identity;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfReader;
import org.briarproject.bramble.api.data.ObjectReader;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;

@Immutable
@NotNullByDefault
class AuthorReader implements ObjectReader<Author> {

	private final AuthorFactory authorFactory;

	AuthorReader(AuthorFactory authorFactory) {
		this.authorFactory = authorFactory;
	}

	@Override
	public Author readObject(BdfReader r) throws IOException {
		r.readListStart();
		String name = r.readString(MAX_AUTHOR_NAME_LENGTH);
		if (name.length() == 0) throw new FormatException();
		byte[] publicKey = r.readRaw(MAX_PUBLIC_KEY_LENGTH);
		r.readListEnd();
		return authorFactory.createAuthor(name, publicKey);
	}
}

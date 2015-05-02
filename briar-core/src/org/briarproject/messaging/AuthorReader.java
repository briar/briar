package org.briarproject.messaging;

import static org.briarproject.api.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.api.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;

import java.io.IOException;

import org.briarproject.api.Author;
import org.briarproject.api.AuthorId;
import org.briarproject.api.FormatException;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.MessageDigest;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.data.Reader;

class AuthorReader implements ObjectReader<Author> {

	private final MessageDigest messageDigest;

	AuthorReader(CryptoComponent crypto) {
		messageDigest = crypto.getMessageDigest();
	}

	public Author readObject(Reader r) throws IOException {
		// Set up the reader
		DigestingConsumer digesting = new DigestingConsumer(messageDigest);
		r.addConsumer(digesting);
		// Read and digest the data
		r.readListStart();
		String name = r.readString(MAX_AUTHOR_NAME_LENGTH);
		if(name.length() == 0) throw new FormatException();
		byte[] publicKey = r.readBytes(MAX_PUBLIC_KEY_LENGTH);
		r.readListEnd();
		// Reset the reader
		r.removeConsumer(digesting);
		// Build and return the author
		AuthorId id = new AuthorId(messageDigest.digest());
		return new Author(id, name, publicKey);
	}
}

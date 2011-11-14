package net.sf.briar.protocol;

import java.io.IOException;
import java.security.MessageDigest;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorFactory;
import net.sf.briar.api.protocol.AuthorId;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class AuthorReader implements ObjectReader<Author> {

	private final MessageDigest messageDigest;
	private final AuthorFactory authorFactory;

	AuthorReader(CryptoComponent crypto, AuthorFactory authorFactory) {
		messageDigest = crypto.getMessageDigest();
		this.authorFactory = authorFactory;
	}

	public Author readObject(Reader r) throws IOException {
		// Initialise the consumer
		DigestingConsumer digesting = new DigestingConsumer(messageDigest);
		messageDigest.reset();
		// Read and digest the data
		r.addConsumer(digesting);
		r.readUserDefinedId(Types.AUTHOR);
		String name = r.readString(ProtocolConstants.MAX_AUTHOR_NAME_LENGTH);
		byte[] publicKey = r.readBytes(ProtocolConstants.MAX_PUBLIC_KEY_LENGTH);
		r.removeConsumer(digesting);
		// Build and return the author
		AuthorId id = new AuthorId(messageDigest.digest());
		return authorFactory.createAuthor(id, name, publicKey);
	}
}

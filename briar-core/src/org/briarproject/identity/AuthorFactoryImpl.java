package org.briarproject.identity;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.system.Clock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.inject.Inject;

class AuthorFactoryImpl implements AuthorFactory {

	private final CryptoComponent crypto;
	private final BdfWriterFactory bdfWriterFactory;
	private final Clock clock;

	@Inject
	AuthorFactoryImpl(CryptoComponent crypto, BdfWriterFactory bdfWriterFactory,
			Clock clock) {
		this.crypto = crypto;
		this.bdfWriterFactory = bdfWriterFactory;
		this.clock = clock;
	}

	public Author createAuthor(String name, byte[] publicKey) {
		return new Author(getId(name, publicKey), name, publicKey);
	}

	public LocalAuthor createLocalAuthor(String name, byte[] publicKey,
			byte[] privateKey) {
		return new LocalAuthor(getId(name, publicKey), name, publicKey,
				privateKey, clock.currentTimeMillis());
	}

	private AuthorId getId(String name, byte[] publicKey) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter w = bdfWriterFactory.createWriter(out);
		try {
			w.writeListStart();
			w.writeString(name);
			w.writeRaw(publicKey);
			w.writeListEnd();
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayOutputStream
			throw new RuntimeException(e);
		}
		return new AuthorId(crypto.hash(AuthorId.LABEL, out.toByteArray()));
	}
}

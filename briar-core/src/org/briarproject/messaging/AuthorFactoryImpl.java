package org.briarproject.messaging;

import static org.briarproject.api.messaging.Types.AUTHOR;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.inject.Inject;

import org.briarproject.api.Author;
import org.briarproject.api.AuthorFactory;
import org.briarproject.api.AuthorId;
import org.briarproject.api.LocalAuthor;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.MessageDigest;
import org.briarproject.api.serial.Writer;
import org.briarproject.api.serial.WriterFactory;

class AuthorFactoryImpl implements AuthorFactory {

	private final CryptoComponent crypto;
	private final WriterFactory writerFactory;

	@Inject
	AuthorFactoryImpl(CryptoComponent crypto, WriterFactory writerFactory) {
		this.crypto = crypto;
		this.writerFactory = writerFactory;
	}

	public Author createAuthor(String name, byte[] publicKey) {
		return new Author(getId(name, publicKey), name, publicKey);
	}

	public LocalAuthor createLocalAuthor(String name, byte[] publicKey,
			byte[] privateKey) {
		return new LocalAuthor(getId(name, publicKey), name, publicKey,
				privateKey);
	}

	private AuthorId getId(String name, byte[] publicKey) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		try {
			w.writeStructStart(AUTHOR);
			w.writeString(name);
			w.writeBytes(publicKey);
			w.writeStructEnd();
		} catch(IOException e) {
			// Shouldn't happen with ByteArrayOutputStream
			throw new RuntimeException();
		}
		MessageDigest messageDigest = crypto.getMessageDigest();
		messageDigest.update(out.toByteArray());
		return new AuthorId(messageDigest.digest());
	}
}

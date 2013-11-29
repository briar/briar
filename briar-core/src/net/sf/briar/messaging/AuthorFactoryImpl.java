package net.sf.briar.messaging;

import static net.sf.briar.api.messaging.Types.AUTHOR;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.inject.Inject;

import net.sf.briar.api.Author;
import net.sf.briar.api.AuthorFactory;
import net.sf.briar.api.AuthorId;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class AuthorFactoryImpl implements AuthorFactory {

	private final CryptoComponent crypto;
	private final WriterFactory writerFactory;

	@Inject
	AuthorFactoryImpl(CryptoComponent crypto, WriterFactory writerFactory) {
		this.crypto = crypto;
		this.writerFactory = writerFactory;
	}

	public Author createAuthor(String name, byte[] publicKey)
			throws IOException {
		return new Author(getId(name, publicKey), name, publicKey);
	}

	public LocalAuthor createLocalAuthor(String name, byte[] publicKey,
			byte[] privateKey) throws IOException {
		return new LocalAuthor(getId(name, publicKey), name, publicKey,
				privateKey);
	}

	private AuthorId getId(String name, byte[] publicKey) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructStart(AUTHOR);
		w.writeString(name);
		w.writeBytes(publicKey);
		w.writeStructEnd();
		MessageDigest messageDigest = crypto.getMessageDigest();
		messageDigest.update(out.toByteArray());
		return new AuthorId(messageDigest.digest());
	}
}

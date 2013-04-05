package net.sf.briar.api;

import java.io.IOException;

public interface AuthorFactory {

	Author createAuthor(String name, byte[] publicKey) throws IOException;

	LocalAuthor createLocalAuthor(String name, byte[] publicKey,
			byte[] privateKey) throws IOException;
}

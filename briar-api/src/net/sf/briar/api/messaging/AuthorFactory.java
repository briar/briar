package net.sf.briar.api.messaging;

import java.io.IOException;

public interface AuthorFactory {

	Author createAuthor(String name, byte[] publicKey) throws IOException;
}

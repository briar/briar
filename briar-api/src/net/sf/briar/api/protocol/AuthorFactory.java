package net.sf.briar.api.protocol;

import java.io.IOException;

public interface AuthorFactory {

	Author createAuthor(String name, byte[] publicKey) throws IOException;
}

package net.sf.briar.api.protocol.writers;

import java.io.IOException;

import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.serial.Writer;

public interface AuthorWriter {

	void writeAuthor(Writer w, Author a) throws IOException;
}

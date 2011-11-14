package net.sf.briar.protocol.writers;

import java.io.IOException;

import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.writers.AuthorWriter;
import net.sf.briar.api.serial.Writer;

class AuthorWriterImpl implements AuthorWriter {

	public void writeAuthor(Writer w, Author a) throws IOException {
		w.writeUserDefinedId(Types.AUTHOR);
		w.writeString(a.getName());
		w.writeBytes(a.getPublicKey());
	}
}

package net.sf.briar.api.protocol;

import java.io.IOException;
import java.util.Arrays;

import net.sf.briar.api.serial.Writer;

/** Type-safe wrapper for a byte array that uniquely identifies an author. */
public class AuthorId extends UniqueId {

	public AuthorId(byte[] id) {
		super(id);
	}

	public void writeTo(Writer w) throws IOException {
		w.writeUserDefinedTag(Tags.AUTHOR_ID);
		w.writeRaw(id);
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof AuthorId)
			return Arrays.equals(id, ((AuthorId) o).id);
		return false;
	}
}

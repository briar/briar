package net.sf.briar.api.serial;

import java.io.IOException;

public interface Writable {

	void writeTo(Writer w) throws IOException;
}

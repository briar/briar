package net.sf.briar.api.serial;

import java.io.InputStream;

public interface ReaderFactory {

	Reader createReader(InputStream in);
}

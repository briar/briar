package org.briarproject.api.serial;

import java.io.InputStream;

public interface ReaderFactory {

	Reader createReader(InputStream in);
}

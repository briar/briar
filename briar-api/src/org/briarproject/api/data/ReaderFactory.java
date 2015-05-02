package org.briarproject.api.data;

import java.io.InputStream;

public interface ReaderFactory {

	Reader createReader(InputStream in);
}

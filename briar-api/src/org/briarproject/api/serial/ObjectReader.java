package org.briarproject.api.serial;

import java.io.IOException;

public interface ObjectReader<T> {

	T readObject(Reader r) throws IOException;
}

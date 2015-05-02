package org.briarproject.api.data;

import java.io.IOException;

public interface ObjectReader<T> {

	T readObject(Reader r) throws IOException;
}

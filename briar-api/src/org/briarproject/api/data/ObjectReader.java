package org.briarproject.api.data;

import java.io.IOException;

public interface ObjectReader<T> {

	T readObject(BdfReader r) throws IOException;
}

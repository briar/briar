package org.briarproject.bramble.api.data;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;

@NotNullByDefault
public interface ObjectReader<T> {

	T readObject(BdfReader r) throws IOException;
}

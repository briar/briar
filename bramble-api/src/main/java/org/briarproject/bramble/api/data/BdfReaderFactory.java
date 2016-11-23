package org.briarproject.bramble.api.data;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.InputStream;

@NotNullByDefault
public interface BdfReaderFactory {

	BdfReader createReader(InputStream in);

	BdfReader createReader(InputStream in, int nestedLimit);
}

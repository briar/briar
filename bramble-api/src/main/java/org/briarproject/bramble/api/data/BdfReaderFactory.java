package org.briarproject.bramble.api.data;

import org.briarproject.nullsafety.NotNullByDefault;

import java.io.InputStream;

@NotNullByDefault
public interface BdfReaderFactory {

	BdfReader createReader(InputStream in);

	/**
	 * Transitional alternative to {@link #createReader(InputStream)} that
	 * can create a reader that accepts non-canonical input, for backward
	 * compatibility.
	 */
	@Deprecated
	BdfReader createReader(InputStream in, boolean canonical);

	BdfReader createReader(InputStream in, int nestedLimit,
			int maxBufferSize, boolean canonical);
}

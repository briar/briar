package org.briarproject.bramble.api.record;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.EOFException;
import java.io.IOException;

@NotNullByDefault
public interface RecordReader {

	/**
	 * Reads and returns the next record.
	 *
	 * @throws EOFException if the end of the stream is reached without reading
	 * a complete record
	 */
	Record readRecord() throws IOException;

	void close() throws IOException;
}

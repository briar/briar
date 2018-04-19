package org.briarproject.bramble.api.record;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;

@NotNullByDefault
public interface RecordWriter {

	void writeRecord(Record r) throws IOException;

	void flush() throws IOException;

	void close() throws IOException;
}

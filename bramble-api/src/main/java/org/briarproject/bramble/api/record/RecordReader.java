package org.briarproject.bramble.api.record;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.Predicate;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.EOFException;
import java.io.IOException;

import javax.annotation.Nullable;

@NotNullByDefault
public interface RecordReader {

	/**
	 * Reads and returns the next record.
	 *
	 * @throws EOFException if the end of the stream is reached without reading
	 * a complete record
	 */
	Record readRecord() throws IOException;

	/**
	 * Reads and returns the next record matching the 'accept' predicate,
	 * skipping any records that match the 'ignore' predicate. Returns null if
	 * no record matching the 'accept' predicate is found before the end of the
	 * stream.
	 *
	 * @throws EOFException If the end of the stream is reached without
	 * reading a complete record
	 * @throws FormatException If a record is read that does not match the
	 * 'accept' or 'ignore' predicates
	 */
	@Nullable
	Record readRecord(Predicate<Record> accept, Predicate<Record> ignore)
			throws IOException;

	void close() throws IOException;
}

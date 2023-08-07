package org.briarproject.bramble.api.data;

import org.briarproject.bramble.api.FormatException;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * An interface for writing BDF objects to an output stream. The BDF output
 * is in canonical form, ie integers and length fields are represented using
 * the minimum number of bytes and dictionary keys are unique and sorted in
 * lexicographic order.
 */
public interface BdfWriter {

	/**
	 * Flushes the writer's output stream.
	 */
	void flush() throws IOException;

	/**
	 * Closes the writer's output stream.
	 */
	void close() throws IOException;

	/**
	 * Writes a BDF null to the output stream.
	 */
	void writeNull() throws IOException;

	/**
	 * Writes a BDF boolean to the output stream.
	 */
	void writeBoolean(boolean b) throws IOException;

	/**
	 * Writes a BDF integer (which has the same range as a Java long) to the
	 * output stream.
	 */
	void writeLong(long l) throws IOException;

	/**
	 * Writes a BDF float (which has the same range as a Java double) to the
	 * output stream.
	 */
	void writeDouble(double d) throws IOException;

	/**
	 * Writes a BDF string (which uses UTF-8 encoding) to the output stream.
	 */
	void writeString(String s) throws IOException;

	/**
	 * Writes a BDF raw to the output stream.
	 */
	void writeRaw(byte[] b) throws IOException;

	/**
	 * Writes a BDF list to the output stream.
	 *
	 * @throws FormatException if the contents of the given collection cannot
	 * be represented as (nested) BDF objects.
	 */
	void writeList(Collection<?> c) throws IOException;

	/**
	 * Writes a BDF dictionary to the output stream.
	 *
	 * @throws FormatException if the contents of the given map cannot be
	 * represented as (nested) BDF objects.
	 */
	void writeDictionary(Map<?, ?> m) throws IOException;
}

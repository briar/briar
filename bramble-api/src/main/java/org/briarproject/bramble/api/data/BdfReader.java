package org.briarproject.bramble.api.data;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.IOException;

/**
 * An interface for reading BDF objects from an input stream.
 * <p>
 * The readX() methods throw {@link FormatException} if the data is not in
 * canonical form, but the hasX() and skipX() methods do not check for
 * canonical form.
 */
@NotNullByDefault
public interface BdfReader {

	int DEFAULT_NESTED_LIMIT = 5;
	int DEFAULT_MAX_BUFFER_SIZE = 64 * 1024;

	/**
	 * Returns true if the reader has reached the end of its input stream.
	 */
	boolean eof() throws IOException;

	/**
	 * Closes the reader's input stream.
	 */
	void close() throws IOException;

	/**
	 * Returns true if the next object in the input is a BDF null.
	 */
	boolean hasNull() throws IOException;

	/**
	 * Reads a BDF null from the input.
	 */
	void readNull() throws IOException;

	/**
	 * Skips over a BDF null.
	 */
	void skipNull() throws IOException;

	/**
	 * Returns true if the next object in the input is a BDF boolean.
	 */
	boolean hasBoolean() throws IOException;

	/**
	 * Reads a BDF boolean from the input and returns it.
	 */
	boolean readBoolean() throws IOException;

	/**
	 * Skips over a BDF boolean.
	 */
	void skipBoolean() throws IOException;

	/**
	 * Returns true if the next object in the input is a BDF integer, which
	 * has the same range as a Java long.
	 */
	boolean hasLong() throws IOException;

	/**
	 * Reads a BDF integer from the input and returns it as a Java long.
	 */
	long readLong() throws IOException;

	/**
	 * Skips over a BDF integer.
	 */
	void skipLong() throws IOException;

	/**
	 * Returns true if the next object in the input is a BDF integer and the
	 * value would fit within the range of a Java int.
	 */
	boolean hasInt() throws IOException;

	/**
	 * Reads a BDF integer from the input and returns it as a Java int.
	 *
	 * @throws FormatException if the value exceeds the range of a Java int.
	 */
	int readInt() throws IOException;

	/**
	 * Skips over a BDF integer.
	 *
	 * @throws FormatException if the value exceeds the range of a Java int.
	 */
	void skipInt() throws IOException;

	/**
	 * Returns true if the next object in the input is a BDF float, which has
	 * the same range as a Java double.
	 */
	boolean hasDouble() throws IOException;

	/**
	 * Reads a BDF float from the input and returns it as a Java double.
	 */
	double readDouble() throws IOException;

	/**
	 * Skips over a BDF float.
	 */
	void skipDouble() throws IOException;

	/**
	 * Returns true if the next object in the input is a BDF string.
	 */
	boolean hasString() throws IOException;

	/**
	 * Reads a BDF string from the input.
	 *
	 * @throws IOException If the string is not valid UTF-8.
	 */
	String readString() throws IOException;

	/**
	 * Skips over a BDF string without checking whether it is valid UTF-8.
	 */
	void skipString() throws IOException;

	/**
	 * Returns true if the next object in the input is a BDF raw.
	 */
	boolean hasRaw() throws IOException;

	/**
	 * Reads a BDF raw from the input and returns it as a byte array.
	 */
	byte[] readRaw() throws IOException;

	/**
	 * Skips over a BDF raw.
	 */
	void skipRaw() throws IOException;

	/**
	 * Returns true if the next object in the input is a BDF list.
	 */
	boolean hasList() throws IOException;

	/**
	 * Reads a BDF list from the input and returns it. The list's contents
	 * are parsed and validated.
	 */
	BdfList readList() throws IOException;

	/**
	 * Skips over a BDF list. The list's contents are parsed (to determine
	 * their length) but not validated.
	 */
	void skipList() throws IOException;

	/**
	 * Returns true if the next object in the input is a BDF dictionary.
	 */
	boolean hasDictionary() throws IOException;

	/**
	 * Reads a BDF dictionary from the input and returns it. The dictionary's
	 * contents are parsed and validated.
	 */
	BdfDictionary readDictionary() throws IOException;

	/**
	 * Skips over a BDF dictionary. The dictionary's contents are parsed
	 * (to determine their length) but not validated.
	 */
	void skipDictionary() throws IOException;
}

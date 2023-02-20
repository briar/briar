package org.briarproject.bramble.api.data;

import org.briarproject.nullsafety.NotNullByDefault;

import java.io.IOException;

@NotNullByDefault
public interface BdfReader {

	int DEFAULT_NESTED_LIMIT = 5;
	int DEFAULT_MAX_BUFFER_SIZE = 64 * 1024;

	boolean eof() throws IOException;

	void close() throws IOException;

	boolean hasNull() throws IOException;

	void readNull() throws IOException;

	void skipNull() throws IOException;

	boolean hasBoolean() throws IOException;

	boolean readBoolean() throws IOException;

	void skipBoolean() throws IOException;

	boolean hasLong() throws IOException;

	long readLong() throws IOException;

	void skipLong() throws IOException;

	boolean hasInt() throws IOException;

	int readInt() throws IOException;

	void skipInt() throws IOException;

	boolean hasDouble() throws IOException;

	double readDouble() throws IOException;

	void skipDouble() throws IOException;

	boolean hasString() throws IOException;

	String readString() throws IOException;

	void skipString() throws IOException;

	boolean hasRaw() throws IOException;

	byte[] readRaw() throws IOException;

	void skipRaw() throws IOException;

	boolean hasList() throws IOException;

	BdfList readList() throws IOException;

	void skipList() throws IOException;

	boolean hasDictionary() throws IOException;

	BdfDictionary readDictionary() throws IOException;

	void skipDictionary() throws IOException;
}

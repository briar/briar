package org.briarproject.bramble.api.data;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;

@NotNullByDefault
public interface BdfReader {

	int DEFAULT_NESTED_LIMIT = 5;

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

	boolean hasDouble() throws IOException;

	double readDouble() throws IOException;

	void skipDouble() throws IOException;

	boolean hasString() throws IOException;

	String readString(int maxLength) throws IOException;

	void skipString() throws IOException;

	boolean hasRaw() throws IOException;

	byte[] readRaw(int maxLength) throws IOException;

	void skipRaw() throws IOException;

	boolean hasList() throws IOException;

	BdfList readList() throws IOException;

	void readListStart() throws IOException;

	boolean hasListEnd() throws IOException;

	void readListEnd() throws IOException;

	void skipList() throws IOException;

	boolean hasDictionary() throws IOException;

	BdfDictionary readDictionary() throws IOException;

	void readDictionaryStart() throws IOException;

	boolean hasDictionaryEnd() throws IOException;

	void readDictionaryEnd() throws IOException;

	void skipDictionary() throws IOException;
}

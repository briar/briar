package org.briarproject.api.data;

import java.io.IOException;

public interface Reader {

	boolean eof() throws IOException;
	void close() throws IOException;

	void addConsumer(Consumer c);
	void removeConsumer(Consumer c);

	boolean hasNull() throws IOException;
	void readNull() throws IOException;
	void skipNull() throws IOException;

	boolean hasBoolean() throws IOException;
	boolean readBoolean() throws IOException;
	void skipBoolean() throws IOException;

	boolean hasInteger() throws IOException;
	long readInteger() throws IOException;
	void skipInteger() throws IOException;

	boolean hasFloat() throws IOException;
	double readFloat() throws IOException;
	void skipFloat() throws IOException;

	boolean hasString() throws IOException;
	String readString(int maxLength) throws IOException;
	void skipString() throws IOException;

	boolean hasRaw() throws IOException;
	byte[] readRaw(int maxLength) throws IOException;
	void skipRaw() throws IOException;

	boolean hasList() throws IOException;
	void readListStart() throws IOException;
	boolean hasListEnd() throws IOException;
	void readListEnd() throws IOException;
	void skipList() throws IOException;

	boolean hasMap() throws IOException;
	void readMapStart() throws IOException;
	boolean hasMapEnd() throws IOException;
	void readMapEnd() throws IOException;
	void skipMap() throws IOException;
}

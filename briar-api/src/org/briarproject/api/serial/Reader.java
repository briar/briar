package org.briarproject.api.serial;

import java.io.IOException;

public interface Reader {

	boolean eof() throws IOException;
	void close() throws IOException;

	void addConsumer(Consumer c);
	void removeConsumer(Consumer c);

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
	void skipString(int maxLength) throws IOException;

	boolean hasBytes() throws IOException;
	byte[] readBytes(int maxLength) throws IOException;
	void skipBytes(int maxLength) throws IOException;

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

	boolean hasStruct() throws IOException;
	boolean hasStruct(int id) throws IOException;
	void readStructStart(int id) throws IOException;
	boolean hasStructEnd() throws IOException;
	void readStructEnd() throws IOException;
	void skipStruct() throws IOException;

	boolean hasNull() throws IOException;
	void readNull() throws IOException;
	void skipNull() throws IOException;
}

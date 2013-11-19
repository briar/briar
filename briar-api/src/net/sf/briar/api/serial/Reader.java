package net.sf.briar.api.serial;

import java.io.IOException;

public interface Reader {

	boolean eof() throws IOException;
	void close() throws IOException;

	void addConsumer(Consumer c);
	void removeConsumer(Consumer c);

	boolean hasBoolean() throws IOException;
	boolean readBoolean() throws IOException;
	void skipBoolean() throws IOException;

	boolean hasUint7() throws IOException;
	byte readUint7() throws IOException;
	void skipUint7() throws IOException;

	boolean hasInt8() throws IOException;
	byte readInt8() throws IOException;
	void skipInt8() throws IOException;

	boolean hasInt16() throws IOException;
	short readInt16() throws IOException;
	void skipInt16() throws IOException;

	boolean hasInt32() throws IOException;
	int readInt32() throws IOException;
	void skipInt32() throws IOException;

	boolean hasInt64() throws IOException;
	long readInt64() throws IOException;
	void skipInt64() throws IOException;

	boolean hasIntAny() throws IOException;
	long readIntAny() throws IOException;
	void skipIntAny() throws IOException;

	boolean hasFloat32() throws IOException;
	float readFloat32() throws IOException;
	void skipFloat32() throws IOException;

	boolean hasFloat64() throws IOException;
	double readFloat64() throws IOException;
	void skipFloat64() throws IOException;

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

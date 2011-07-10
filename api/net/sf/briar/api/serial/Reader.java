package net.sf.briar.api.serial;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface Reader {

	boolean eof() throws IOException;

	boolean hasBoolean() throws IOException;
	boolean readBoolean() throws IOException;

	boolean hasUint7() throws IOException;
	byte readUint7() throws IOException;
	boolean hasInt8() throws IOException;
	byte readInt8() throws IOException;
	boolean hasInt16() throws IOException;
	short readInt16() throws IOException;
	boolean hasInt32() throws IOException;
	int readInt32() throws IOException;
	boolean hasInt64() throws IOException;
	long readInt64() throws IOException;
	boolean hasIntAny() throws IOException;
	long readIntAny() throws IOException;

	boolean hasFloat32() throws IOException;
	float readFloat32() throws IOException;
	boolean hasFloat64() throws IOException;
	double readFloat64() throws IOException;

	boolean hasUtf8() throws IOException;
	String readUtf8() throws IOException;
	String readUtf8(int maxLength) throws IOException;

	boolean hasRaw() throws IOException;
	byte[] readRaw() throws IOException;
	byte[] readRaw(int maxLength) throws IOException;

	// FIXME: Add type-safe readers and iterator readers

	boolean hasList(boolean definite) throws IOException;
	List<?> readList(boolean definite) throws IOException;
	boolean hasList() throws IOException;
	List<?> readList() throws IOException;

	boolean hasMap(boolean definite) throws IOException;
	Map<?, ?> readMap(boolean definite) throws IOException;
	boolean hasMap() throws IOException;
	Map<?, ?> readMap() throws IOException;

	boolean hasNull() throws IOException;
	void readNull() throws IOException;
}

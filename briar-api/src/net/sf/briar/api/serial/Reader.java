package net.sf.briar.api.serial;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface Reader {

	boolean eof() throws IOException;
	void close() throws IOException;

	void setMaxStringLength(int length);
	void resetMaxStringLength();

	void setMaxBytesLength(int length);
	void resetMaxBytesLength();

	void addConsumer(Consumer c);
	void removeConsumer(Consumer c);

	void addStructReader(int id, StructReader<?> r);
	void removeStructReader(int id);

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

	boolean hasString() throws IOException;
	String readString() throws IOException;
	String readString(int maxLength) throws IOException;

	boolean hasBytes() throws IOException;
	byte[] readBytes() throws IOException;
	byte[] readBytes(int maxLength) throws IOException;

	boolean hasList() throws IOException;
	<E> List<E> readList(Class<E> e) throws IOException;
	boolean hasListStart() throws IOException;
	void readListStart() throws IOException;
	boolean hasListEnd() throws IOException;
	void readListEnd() throws IOException;

	boolean hasMap() throws IOException;
	<K, V> Map<K, V> readMap(Class<K> k, Class<V> v) throws IOException;
	boolean hasMapStart() throws IOException;
	void readMapStart() throws IOException;
	boolean hasMapEnd() throws IOException;
	void readMapEnd() throws IOException;

	boolean hasNull() throws IOException;
	void readNull() throws IOException;

	boolean hasStruct(int id) throws IOException;
	<T> T readStruct(int id, Class<T> t) throws IOException;
	void readStructId(int id) throws IOException;
}

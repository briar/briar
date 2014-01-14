package org.briarproject.api.serial;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public interface Writer {

	void flush() throws IOException;
	void close() throws IOException;

	void addConsumer(Consumer c);
	void removeConsumer(Consumer c);

	void writeBoolean(boolean b) throws IOException;
	void writeInteger(long l) throws IOException;
	void writeFloat(double d) throws IOException;
	void writeString(String s) throws IOException;
	void writeBytes(byte[] b) throws IOException;

	void writeList(Collection<?> c) throws IOException;
	void writeListStart() throws IOException;
	void writeListEnd() throws IOException;

	void writeMap(Map<?, ?> m) throws IOException;
	void writeMapStart() throws IOException;
	void writeMapEnd() throws IOException;

	void writeStructStart(int id) throws IOException;
	void writeStructEnd() throws IOException;

	void writeNull() throws IOException;
}

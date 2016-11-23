package org.briarproject.bramble.api.data;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public interface BdfWriter {

	void flush() throws IOException;

	void close() throws IOException;

	void writeNull() throws IOException;

	void writeBoolean(boolean b) throws IOException;

	void writeLong(long l) throws IOException;

	void writeDouble(double d) throws IOException;

	void writeString(String s) throws IOException;

	void writeRaw(byte[] b) throws IOException;

	void writeList(Collection<?> c) throws IOException;

	void writeListStart() throws IOException;

	void writeListEnd() throws IOException;

	void writeDictionary(Map<?, ?> m) throws IOException;

	void writeDictionaryStart() throws IOException;

	void writeDictionaryEnd() throws IOException;
}

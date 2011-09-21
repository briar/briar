package net.sf.briar.api.serial;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public interface Writer {

	void writeBoolean(boolean b) throws IOException;

	void writeUint7(byte b) throws IOException;
	void writeInt8(byte b) throws IOException;
	void writeInt16(short s) throws IOException;
	void writeInt32(int i) throws IOException;
	void writeInt64(long l) throws IOException;
	void writeIntAny(long l) throws IOException;

	void writeFloat32(float f) throws IOException;
	void writeFloat64(double d) throws IOException;

	void writeString(String s) throws IOException;
	void writeBytes(byte[] b) throws IOException;

	void writeList(Collection<?> c) throws IOException;
	void writeListStart() throws IOException;
	void writeListEnd() throws IOException;

	void writeMap(Map<?, ?> m) throws IOException;
	void writeMapStart() throws IOException;
	void writeMapEnd() throws IOException;

	void writeNull() throws IOException;

	void writeUserDefinedId(int tag) throws IOException;
}

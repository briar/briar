package net.sf.briar.api.serial;

import java.io.IOException;

public interface StructReader<T> {

	T readStruct(Reader r) throws IOException;
}

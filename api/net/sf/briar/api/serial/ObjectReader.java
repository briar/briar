package net.sf.briar.api.serial;

import java.io.IOException;
import java.security.GeneralSecurityException;

public interface ObjectReader<T> {

	T readObject(Reader r) throws IOException, GeneralSecurityException;
}

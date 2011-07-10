package net.sf.briar.serial;

import java.io.InputStream;

import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;

public class ReaderFactoryImpl implements ReaderFactory {

	public Reader createReader(InputStream in) {
		return new ReaderImpl(in);
	}
}

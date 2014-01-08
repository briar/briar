package org.briarproject.serial;

import java.io.InputStream;

import org.briarproject.api.serial.Reader;
import org.briarproject.api.serial.ReaderFactory;

class ReaderFactoryImpl implements ReaderFactory {

	public Reader createReader(InputStream in) {
		return new ReaderImpl(in);
	}
}

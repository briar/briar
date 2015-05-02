package org.briarproject.data;

import java.io.InputStream;

import org.briarproject.api.data.Reader;
import org.briarproject.api.data.ReaderFactory;

class ReaderFactoryImpl implements ReaderFactory {

	public Reader createReader(InputStream in) {
		return new ReaderImpl(in);
	}
}

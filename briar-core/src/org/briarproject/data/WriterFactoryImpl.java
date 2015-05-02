package org.briarproject.data;

import java.io.OutputStream;

import org.briarproject.api.data.Writer;
import org.briarproject.api.data.WriterFactory;

class WriterFactoryImpl implements WriterFactory {

	public Writer createWriter(OutputStream out) {
		return new WriterImpl(out);
	}
}

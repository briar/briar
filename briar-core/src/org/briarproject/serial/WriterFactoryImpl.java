package org.briarproject.serial;

import java.io.OutputStream;

import org.briarproject.api.serial.Writer;
import org.briarproject.api.serial.WriterFactory;

class WriterFactoryImpl implements WriterFactory {

	public Writer createWriter(OutputStream out) {
		return new WriterImpl(out);
	}
}

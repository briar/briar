package net.sf.briar.serial;

import java.io.OutputStream;

import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class WriterFactoryImpl implements WriterFactory {

	public Writer createWriter(OutputStream out) {
		return new WriterImpl(out);
	}
}

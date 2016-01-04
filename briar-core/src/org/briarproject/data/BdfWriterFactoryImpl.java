package org.briarproject.data;

import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;

import java.io.OutputStream;

class BdfWriterFactoryImpl implements BdfWriterFactory {

	public BdfWriter createWriter(OutputStream out) {
		return new BdfWriterImpl(out);
	}
}

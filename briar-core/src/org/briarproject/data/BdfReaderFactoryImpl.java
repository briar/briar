package org.briarproject.data;

import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;

import java.io.InputStream;

class BdfReaderFactoryImpl implements BdfReaderFactory {

	public BdfReader createReader(InputStream in) {
		return new BdfReaderImpl(in);
	}
}

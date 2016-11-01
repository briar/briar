package org.briarproject.data;

import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;

import java.io.InputStream;

import static org.briarproject.api.data.BdfReader.DEFAULT_NESTED_LIMIT;

class BdfReaderFactoryImpl implements BdfReaderFactory {

	@Override
	public BdfReader createReader(InputStream in) {
		return new BdfReaderImpl(in, DEFAULT_NESTED_LIMIT);
	}

	@Override
	public BdfReader createReader(InputStream in, int nestedLimit) {
		return new BdfReaderImpl(in, nestedLimit);
	}
}

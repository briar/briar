package org.briarproject.data;

import org.briarproject.api.data.ReaderFactory;
import org.briarproject.api.data.WriterFactory;

import com.google.inject.AbstractModule;

public class DataModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ReaderFactory.class).to(ReaderFactoryImpl.class);
		bind(WriterFactory.class).to(WriterFactoryImpl.class);
	}
}

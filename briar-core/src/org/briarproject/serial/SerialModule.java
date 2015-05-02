package org.briarproject.serial;

import org.briarproject.api.serial.ReaderFactory;
import org.briarproject.api.serial.WriterFactory;

import com.google.inject.AbstractModule;

public class SerialModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ReaderFactory.class).to(ReaderFactoryImpl.class);
		bind(WriterFactory.class).to(WriterFactoryImpl.class);
	}
}

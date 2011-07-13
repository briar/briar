package net.sf.briar.serial;

import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.WriterFactory;

import com.google.inject.AbstractModule;

public class SerialModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ReaderFactory.class).to(ReaderFactoryImpl.class);
		bind(WriterFactory.class).to(WriterFactoryImpl.class);
	}
}

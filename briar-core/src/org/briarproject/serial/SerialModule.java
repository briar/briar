package org.briarproject.serial;

import javax.inject.Singleton;

import org.briarproject.api.serial.ReaderFactory;
import org.briarproject.api.serial.SerialComponent;
import org.briarproject.api.serial.WriterFactory;

import com.google.inject.AbstractModule;

public class SerialModule extends AbstractModule {

	protected void configure() {
		bind(ReaderFactory.class).to(ReaderFactoryImpl.class);
		bind(SerialComponent.class).to(
				SerialComponentImpl.class).in(Singleton.class);
		bind(WriterFactory.class).to(WriterFactoryImpl.class);
	}
}

package net.sf.briar.serial;

import javax.inject.Singleton;

import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.SerialComponent;
import net.sf.briar.api.serial.WriterFactory;

import com.google.inject.AbstractModule;

public class SerialModule extends AbstractModule {

	protected void configure() {
		bind(ReaderFactory.class).to(ReaderFactoryImpl.class);
		bind(SerialComponent.class).to(
				SerialComponentImpl.class).in(Singleton.class);
		bind(WriterFactory.class).to(WriterFactoryImpl.class);
	}
}

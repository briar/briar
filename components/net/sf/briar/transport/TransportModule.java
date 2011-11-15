package net.sf.briar.transport;

import net.sf.briar.api.transport.ConnectionContextFactory;
import net.sf.briar.api.transport.ConnectionDispatcher;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.ConnectionWindowFactory;
import net.sf.briar.api.transport.ConnectionWriterFactory;

import com.google.inject.AbstractModule;

public class TransportModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ConnectionContextFactory.class).to(
				ConnectionContextFactoryImpl.class);
		bind(ConnectionDispatcher.class).to(ConnectionDispatcherImpl.class);
		bind(ConnectionReaderFactory.class).to(
				ConnectionReaderFactoryImpl.class);
		bind(ConnectionRecogniser.class).to(ConnectionRecogniserImpl.class);
		bind(ConnectionWindowFactory.class).to(
				ConnectionWindowFactoryImpl.class);
		bind(ConnectionWriterFactory.class).to(
				ConnectionWriterFactoryImpl.class);
	}
}

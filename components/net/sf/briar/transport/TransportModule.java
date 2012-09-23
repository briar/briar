package net.sf.briar.transport;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import net.sf.briar.api.transport.ConnectionDispatcher;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.IncomingConnectionExecutor;

import com.google.inject.AbstractModule;

public class TransportModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ConnectionDispatcher.class).to(ConnectionDispatcherImpl.class);
		bind(ConnectionReaderFactory.class).to(
				ConnectionReaderFactoryImpl.class);
		bind(ConnectionRegistry.class).toInstance(new ConnectionRegistryImpl());
		bind(ConnectionWriterFactory.class).to(
				ConnectionWriterFactoryImpl.class);
		// The executor is unbounded, so tasks can be dependent or long-lived
		bind(Executor.class).annotatedWith(
				IncomingConnectionExecutor.class).toInstance(
						Executors.newCachedThreadPool());
	}
}

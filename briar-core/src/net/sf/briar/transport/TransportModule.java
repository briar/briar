package net.sf.briar.transport;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.transport.ConnectionDispatcher;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.IncomingConnectionExecutor;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class TransportModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ConnectionDispatcher.class).to(ConnectionDispatcherImpl.class);
		bind(ConnectionReaderFactory.class).to(
				ConnectionReaderFactoryImpl.class);
		bind(ConnectionRecogniser.class).to(ConnectionRecogniserImpl.class).in(
				Singleton.class);
		bind(ConnectionRegistry.class).toInstance(new ConnectionRegistryImpl());
		bind(ConnectionWriterFactory.class).to(
				ConnectionWriterFactoryImpl.class);
		// The executor is unbounded, so tasks can be dependent or long-lived
		Executor e = Executors.newCachedThreadPool();
		bind(Executor.class).annotatedWith(
				IncomingConnectionExecutor.class).toInstance(e);
		bind(KeyManager.class).to(KeyManagerImpl.class).in(Singleton.class);
	}
}

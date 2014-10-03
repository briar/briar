package org.briarproject.transport;

import javax.inject.Singleton;

import org.briarproject.api.crypto.KeyManager;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.transport.ConnectionDispatcher;
import org.briarproject.api.transport.ConnectionReaderFactory;
import org.briarproject.api.transport.ConnectionRecogniser;
import org.briarproject.api.transport.ConnectionRegistry;
import org.briarproject.api.transport.ConnectionWriterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class TransportModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ConnectionDispatcher.class).to(ConnectionDispatcherImpl.class);
		bind(ConnectionReaderFactory.class).to(
				ConnectionReaderFactoryImpl.class);
		bind(ConnectionRecogniser.class).to(
				ConnectionRecogniserImpl.class).in(Singleton.class);
		bind(ConnectionRegistry.class).to(
				ConnectionRegistryImpl.class).in(Singleton.class);;
				bind(ConnectionWriterFactory.class).to(
						ConnectionWriterFactoryImpl.class);
	}

	@Provides @Singleton
	KeyManager getKeyManager(LifecycleManager lifecycleManager,
			KeyManagerImpl keyManager) {
		lifecycleManager.register(keyManager);
		return keyManager;
	}
}

package org.briarproject.transport;

import javax.inject.Singleton;

import org.briarproject.api.crypto.KeyManager;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.transport.ConnectionDispatcher;
import org.briarproject.api.transport.ConnectionRegistry;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;
import org.briarproject.api.transport.TagRecogniser;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class TransportModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ConnectionDispatcher.class).to(ConnectionDispatcherImpl.class);
		bind(StreamReaderFactory.class).to(
				StreamReaderFactoryImpl.class);
		bind(TagRecogniser.class).to(
				TagRecogniserImpl.class).in(Singleton.class);
		bind(ConnectionRegistry.class).to(
				ConnectionRegistryImpl.class).in(Singleton.class);;
				bind(StreamWriterFactory.class).to(
						StreamWriterFactoryImpl.class);
	}

	@Provides @Singleton
	KeyManager getKeyManager(LifecycleManager lifecycleManager,
			KeyManagerImpl keyManager) {
		lifecycleManager.register(keyManager);
		return keyManager;
	}
}

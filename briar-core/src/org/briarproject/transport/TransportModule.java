package org.briarproject.transport;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import org.briarproject.api.event.EventBus;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;

import javax.inject.Singleton;

public class TransportModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(StreamReaderFactory.class).to(StreamReaderFactoryImpl.class);
		bind(StreamWriterFactory.class).to(StreamWriterFactoryImpl.class);
	}

	@Provides @Singleton
	KeyManager getKeyManager(LifecycleManager lifecycleManager,
			EventBus eventBus, KeyManagerImpl keyManager) {
		lifecycleManager.register(keyManager);
		eventBus.addListener(keyManager);
		return keyManager;
	}
}

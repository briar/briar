package org.briarproject.transport;

import org.briarproject.api.crypto.StreamDecrypterFactory;
import org.briarproject.api.crypto.StreamEncrypterFactory;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class TransportModule {

	public static class EagerSingletons {
		@Inject KeyManager keyManager;
	}

	@Provides
	StreamReaderFactory provideStreamReaderFactory(
			StreamDecrypterFactory streamDecrypterFactory) {
		return new StreamReaderFactoryImpl(streamDecrypterFactory);
	}

	@Provides
	StreamWriterFactory provideStreamWriterFactory(
			StreamEncrypterFactory streamEncrypterFactory) {
		return new StreamWriterFactoryImpl(streamEncrypterFactory);
	}

	@Provides
	@Singleton
	KeyManager getKeyManager(LifecycleManager lifecycleManager,
			EventBus eventBus, KeyManagerImpl keyManager) {
		lifecycleManager.register(keyManager);
		eventBus.addListener(keyManager);
		return keyManager;
	}
}

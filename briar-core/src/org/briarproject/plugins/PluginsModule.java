package org.briarproject.plugins;

import org.briarproject.api.event.EventBus;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.plugins.BackoffFactory;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;

import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class PluginsModule {

	public static class EagerSingletons {
		@Inject
		PluginManager pluginManager;
	}

	@Provides
	BackoffFactory provideBackoffFactory() {
		return new BackoffFactoryImpl();
	}

	@Provides
	@Singleton
	Poller providePoller(PollerImpl poller) {
		return poller;
	}

	@Provides
	ConnectionManager provideConnectionManager(
			@IoExecutor Executor ioExecutor, KeyManager keyManager,
			StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory,
			SyncSessionFactory syncSessionFactory,
			ConnectionRegistry connectionRegistry) {
		return new ConnectionManagerImpl(ioExecutor, keyManager,
				streamReaderFactory, streamWriterFactory, syncSessionFactory,
				connectionRegistry);
	}

	@Provides
	@Singleton
	ConnectionRegistry provideConnectionRegistry(EventBus eventBus) {
		return new ConnectionRegistryImpl(eventBus);
	}

	@Provides
	@Singleton
	PluginManager getPluginManager(LifecycleManager lifecycleManager,
			PluginManagerImpl pluginManager) {
		lifecycleManager.registerService(pluginManager);
		return pluginManager;
	}
}

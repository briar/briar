package org.briarproject.plugins;

import javax.inject.Singleton;

import org.briarproject.api.event.EventBus;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.api.system.Timer;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;

import java.util.concurrent.Executor;

import dagger.Module;
import dagger.Provides;


@Module
public class PluginsModule {

	@Provides
	Poller providePoller(@IoExecutor Executor ioExecutor,
			ConnectionRegistry connectionRegistry, Timer timer) {
		return new PollerImpl(ioExecutor, connectionRegistry, timer);
	}

	@Provides
	ConnectionManager provideConnectionManager(
			@IoExecutor Executor ioExecutor,
			KeyManager keyManager, StreamReaderFactory streamReaderFactory,
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
		lifecycleManager.register(pluginManager);
		return pluginManager;
	}
}

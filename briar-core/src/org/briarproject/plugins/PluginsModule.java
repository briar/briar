package org.briarproject.plugins;

import org.briarproject.api.event.EventBus;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.plugins.BackoffFactory;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.api.system.Clock;
import org.briarproject.api.system.Scheduler;

import java.security.SecureRandom;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class PluginsModule {

	public static class EagerSingletons {
		@Inject
		PluginManager pluginManager;
		@Inject
		Poller poller;
	}

	@Provides
	BackoffFactory provideBackoffFactory() {
		return new BackoffFactoryImpl();
	}

	@Provides
	@Singleton
	Poller providePoller(@IoExecutor Executor ioExecutor,
			@Scheduler ScheduledExecutorService scheduler,
			ConnectionManager connectionManager,
			ConnectionRegistry connectionRegistry, PluginManager pluginManager,
			SecureRandom random, Clock clock, EventBus eventBus) {
		Poller poller = new Poller(ioExecutor, scheduler, connectionManager,
				connectionRegistry, pluginManager, random, clock);
		eventBus.addListener(poller);
		return poller;
	}

	@Provides
	@Singleton
	ConnectionManager provideConnectionManager(
			ConnectionManagerImpl connectionManager) {
		return connectionManager;
	}

	@Provides
	@Singleton
	ConnectionRegistry provideConnectionRegistry(
			ConnectionRegistryImpl connectionRegistry) {
		return connectionRegistry;
	}

	@Provides
	@Singleton
	PluginManager providePluginManager(LifecycleManager lifecycleManager,
			PluginManagerImpl pluginManager) {
		lifecycleManager.registerService(pluginManager);
		return pluginManager;
	}
}

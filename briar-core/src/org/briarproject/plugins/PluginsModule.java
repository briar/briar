package org.briarproject.plugins;

import javax.inject.Singleton;

import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.plugins.ConnectionDispatcher;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.plugins.PluginManager;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class PluginsModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(Poller.class).to(PollerImpl.class);
		bind(ConnectionDispatcher.class).to(ConnectionDispatcherImpl.class);
		bind(ConnectionRegistry.class).to(
				ConnectionRegistryImpl.class).in(Singleton.class);
	}

	@Provides @Singleton
	PluginManager getPluginManager(LifecycleManager lifecycleManager,
			PluginManagerImpl pluginManager) {
		lifecycleManager.register(pluginManager);
		return pluginManager;
	}
}

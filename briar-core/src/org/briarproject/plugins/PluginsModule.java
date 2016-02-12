package org.briarproject.plugins;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.plugins.BackoffFactory;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.plugins.PluginManager;

import javax.inject.Singleton;

public class PluginsModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(BackoffFactory.class).to(BackoffFactoryImpl.class);
		bind(Poller.class).to(PollerImpl.class);
		bind(ConnectionManager.class).to(ConnectionManagerImpl.class);
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

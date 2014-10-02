package org.briarproject.plugins;

import javax.inject.Singleton;

import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.plugins.PluginManager;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class PluginsModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(Poller.class).to(PollerImpl.class);
	}

	@Provides @Singleton
	PluginManager getPluginManager(LifecycleManager lifecycleManager,
			PluginManagerImpl pluginManager) {
		lifecycleManager.register(pluginManager);
		return pluginManager;
	}
}

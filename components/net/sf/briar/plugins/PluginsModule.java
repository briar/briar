package net.sf.briar.plugins;

import net.sf.briar.api.plugins.PluginManager;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class PluginsModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(PluginManager.class).to(
				PluginManagerImpl.class).in(Singleton.class);
		bind(Poller.class).to(PollerImpl.class);
	}
}

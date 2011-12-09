package net.sf.briar.plugins;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.PluginManager;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class PluginsModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ScheduledExecutorService.class).annotatedWith(
				PluginExecutor.class).toInstance(
						Executors.newScheduledThreadPool(1));
		bind(PluginManager.class).to(
				PluginManagerImpl.class).in(Singleton.class);
		bind(Poller.class).to(PollerImpl.class);
	}
}

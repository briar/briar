package net.sf.briar.plugins;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.PluginManager;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class PluginsModule extends AbstractModule {

	@Override
	protected void configure() {
		// The executor is unbounded, so tasks can be dependent or long-lived
		bind(ExecutorService.class).annotatedWith(
				PluginExecutor.class).toInstance(
						Executors.newCachedThreadPool());
		bind(PluginManager.class).to(
				PluginManagerImpl.class).in(Singleton.class);
		bind(Poller.class).to(PollerImpl.class);
	}
}

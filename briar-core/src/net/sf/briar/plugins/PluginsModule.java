package net.sf.briar.plugins;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;

import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.PluginManager;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class PluginsModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(PluginManager.class).to(
				PluginManagerImpl.class).in(Singleton.class);
		bind(Poller.class).to(PollerImpl.class);
		// The thread pool is unbounded, so use direct handoff
		BlockingQueue<Runnable> queue = new SynchronousQueue<Runnable>();
		// Discard tasks that are submitted during shutdown
		RejectedExecutionHandler policy =
				new ThreadPoolExecutor.DiscardPolicy();
		// Create threads as required and keep them in the pool for 60 seconds
		ExecutorService e = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
				60, SECONDS, queue, policy);
		bind(Executor.class).annotatedWith(PluginExecutor.class).toInstance(e);
		bind(ExecutorService.class).annotatedWith(
				PluginExecutor.class).toInstance(e);
	}
}

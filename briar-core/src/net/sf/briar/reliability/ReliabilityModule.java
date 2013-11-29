package net.sf.briar.reliability;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;

import javax.inject.Singleton;

import net.sf.briar.api.lifecycle.LifecycleManager;
import net.sf.briar.api.reliability.ReliabilityExecutor;
import net.sf.briar.api.reliability.ReliabilityLayerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class ReliabilityModule extends AbstractModule {

	private final ExecutorService reliabilityExecutor;

	public ReliabilityModule() {
		// The thread pool is unbounded, so use direct handoff
		BlockingQueue<Runnable> queue = new SynchronousQueue<Runnable>();
		// Discard tasks that are submitted during shutdown
		RejectedExecutionHandler policy =
				new ThreadPoolExecutor.DiscardPolicy();
		// Create threads as required and keep them in the pool for 60 seconds
		reliabilityExecutor = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
				60, SECONDS, queue, policy);
	}

	protected void configure() {
		bind(ReliabilityLayerFactory.class).to(
				ReliabilityLayerFactoryImpl.class);
	}

	@Provides @Singleton @ReliabilityExecutor
	Executor getReliabilityExecutor(LifecycleManager lifecycleManager) {
		lifecycleManager.registerForShutdown(reliabilityExecutor);
		return reliabilityExecutor;
	}
}

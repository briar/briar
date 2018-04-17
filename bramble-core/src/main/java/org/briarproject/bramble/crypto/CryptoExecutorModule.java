package org.briarproject.bramble.crypto;

import org.briarproject.bramble.TimeLoggingExecutor;
import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static java.util.concurrent.TimeUnit.SECONDS;

@Module
public class CryptoExecutorModule {

	public static class EagerSingletons {
		@Inject
		@CryptoExecutor
		ExecutorService cryptoExecutor;
	}

	/**
	 * The maximum number of executor threads.
	 * <p>
	 * The number of available processors can change during the lifetime of the
	 * JVM, so this is just a reasonable guess.
	 */
	private static final int MAX_EXECUTOR_THREADS =
			Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

	private final ExecutorService cryptoExecutor;

	public CryptoExecutorModule() {
		// Use an unbounded queue
		BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
		// Discard tasks that are submitted during shutdown
		RejectedExecutionHandler policy =
				new ThreadPoolExecutor.DiscardPolicy();
		// Create a limited # of threads and keep them in the pool for 60 secs
		cryptoExecutor = new TimeLoggingExecutor("CryptoExecutor", 0,
				MAX_EXECUTOR_THREADS, 60, SECONDS, queue, policy);
	}

	@Provides
	@Singleton
	@CryptoExecutor
	ExecutorService provideCryptoExecutorService(
			LifecycleManager lifecycleManager) {
		lifecycleManager.registerForShutdown(cryptoExecutor);
		return cryptoExecutor;
	}

	@Provides
	@CryptoExecutor
	Executor provideCryptoExecutor() {
		return cryptoExecutor;
	}
}

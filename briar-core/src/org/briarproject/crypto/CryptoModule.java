package org.briarproject.crypto;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import javax.inject.Singleton;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.crypto.PasswordStrengthEstimator;
import org.briarproject.api.lifecycle.LifecycleManager;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class CryptoModule extends AbstractModule {

	/** The maximum number of executor threads. */
	private static final int MAX_EXECUTOR_THREADS =
			Runtime.getRuntime().availableProcessors();

	private final ExecutorService cryptoExecutor;

	public CryptoModule() {
		// The queue is unbounded, so tasks can be dependent
		BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
		// Discard tasks that are submitted during shutdown
		RejectedExecutionHandler policy =
				new ThreadPoolExecutor.DiscardPolicy();
		// Create a limited # of threads and keep them in the pool for 60 secs
		cryptoExecutor = new ThreadPoolExecutor(0, MAX_EXECUTOR_THREADS,
				60, SECONDS, queue, policy);
	}

	protected void configure() {
		bind(CryptoComponent.class).to(
				CryptoComponentImpl.class).in(Singleton.class);
		bind(PasswordStrengthEstimator.class).to(
				PasswordStrengthEstimatorImpl.class);
	}

	@Provides @Singleton @CryptoExecutor
	Executor getCryptoExecutor(LifecycleManager lifecycleManager) {
		lifecycleManager.registerForShutdown(cryptoExecutor);
		return cryptoExecutor;
	}
}

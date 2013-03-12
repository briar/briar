package net.sf.briar.crypto;

import java.util.concurrent.Executor;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.CryptoExecutor;
import net.sf.briar.util.BoundedExecutor;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class CryptoModule extends AbstractModule {

	// FIXME: Determine suitable values for these constants empirically

	/**
	 * The maximum number of tasks that can be queued for execution before
	 * submitting another task will block.
	 */
	private static final int MAX_QUEUED_EXECUTOR_TASKS = 10;

	/** The minimum number of executor threads to keep in the pool. */
	private static final int MIN_EXECUTOR_THREADS = 1;

	/** The maximum number of executor threads. */
	private static final int MAX_EXECUTOR_THREADS =
			Runtime.getRuntime().availableProcessors();

	@Override
	protected void configure() {
		bind(CryptoComponent.class).to(
				CryptoComponentImpl.class).in(Singleton.class);
		// The executor is bounded, so tasks must be independent and short-lived
		bind(Executor.class).annotatedWith(CryptoExecutor.class).toInstance(
				new BoundedExecutor(MAX_QUEUED_EXECUTOR_TASKS,
						MIN_EXECUTOR_THREADS, MAX_EXECUTOR_THREADS));
	}
}

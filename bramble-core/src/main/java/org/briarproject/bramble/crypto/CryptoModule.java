package org.briarproject.bramble.crypto;

import org.briarproject.bramble.TimeLoggingExecutor;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator;
import org.briarproject.bramble.api.crypto.StreamDecrypterFactory;
import org.briarproject.bramble.api.crypto.StreamEncrypterFactory;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.system.SecureRandomProvider;

import java.security.SecureRandom;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static java.util.concurrent.TimeUnit.SECONDS;

@Module
public class CryptoModule {

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

	public CryptoModule() {
		// Use an unbounded queue
		BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
		// Discard tasks that are submitted during shutdown
		RejectedExecutionHandler policy =
				new ThreadPoolExecutor.DiscardPolicy();
		// Create a limited # of threads and keep them in the pool for 60 secs
		cryptoExecutor = new TimeLoggingExecutor("CryptoExecutor", 0,
				MAX_EXECUTOR_THREADS, 60, SECONDS, queue, policy);
	}

	@Provides
	AuthenticatedCipher provideAuthenticatedCipher() {
		return new XSalsa20Poly1305AuthenticatedCipher();
	}

	@Provides
	@Singleton
	CryptoComponent provideCryptoComponent(
			SecureRandomProvider secureRandomProvider) {
		return new CryptoComponentImpl(secureRandomProvider);
	}

	@Provides
	PasswordStrengthEstimator providePasswordStrengthEstimator() {
		return new PasswordStrengthEstimatorImpl();
	}

	@Provides
	StreamDecrypterFactory provideStreamDecrypterFactory(
			Provider<AuthenticatedCipher> cipherProvider) {
		return new StreamDecrypterFactoryImpl(cipherProvider);
	}

	@Provides
	StreamEncrypterFactory provideStreamEncrypterFactory(CryptoComponent crypto,
			Provider<AuthenticatedCipher> cipherProvider) {
		return new StreamEncrypterFactoryImpl(crypto, cipherProvider);
	}

	@Provides
	@Singleton
	@CryptoExecutor
	ExecutorService getCryptoExecutorService(
			LifecycleManager lifecycleManager) {
		lifecycleManager.registerForShutdown(cryptoExecutor);
		return cryptoExecutor;
	}

	@Provides
	@CryptoExecutor
	Executor getCryptoExecutor() {
		return cryptoExecutor;
	}

	@Provides
	SecureRandom getSecureRandom(CryptoComponent crypto) {
		return crypto.getSecureRandom();
	}

}

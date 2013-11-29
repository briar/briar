package net.sf.briar.transport;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;

import javax.inject.Singleton;

import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.lifecycle.LifecycleManager;
import net.sf.briar.api.transport.ConnectionDispatcher;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.IncomingConnectionExecutor;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class TransportModule extends AbstractModule {

	private final ExecutorService incomingConnectionExecutor;

	public TransportModule() {
		// The thread pool is unbounded, so use direct handoff
		BlockingQueue<Runnable> queue = new SynchronousQueue<Runnable>();
		// Discard tasks that are submitted during shutdown
		RejectedExecutionHandler policy =
				new ThreadPoolExecutor.DiscardPolicy();
		// Create threads as required and keep them in the pool for 60 seconds
		incomingConnectionExecutor = new ThreadPoolExecutor(0,
				Integer.MAX_VALUE, 60, SECONDS, queue, policy);
	}

	protected void configure() {
		bind(ConnectionDispatcher.class).to(ConnectionDispatcherImpl.class);
		bind(ConnectionReaderFactory.class).to(
				ConnectionReaderFactoryImpl.class);
		bind(ConnectionRecogniser.class).to(
				ConnectionRecogniserImpl.class).in(Singleton.class);
		bind(ConnectionRegistry.class).toInstance(new ConnectionRegistryImpl());
		bind(ConnectionWriterFactory.class).to(
				ConnectionWriterFactoryImpl.class);
	}

	@Provides @Singleton
	KeyManager getKeyManager(LifecycleManager lifecycleManager,
			KeyManagerImpl keyManager) {
		lifecycleManager.register(keyManager);
		return keyManager;
	}

	@Provides @Singleton @IncomingConnectionExecutor
	Executor getIncomingConnectionExecutor(LifecycleManager lifecycleManager) {
		lifecycleManager.registerForShutdown(incomingConnectionExecutor);
		return incomingConnectionExecutor;
	}
}

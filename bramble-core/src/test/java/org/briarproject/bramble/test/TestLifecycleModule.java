package org.briarproject.bramble.test;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook.Priority;
import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.bramble.api.lifecycle.ShutdownManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.RUNNING;

@Module
public class TestLifecycleModule {

	@Provides
	LifecycleManager provideLifecycleManager() {
		@NotNullByDefault
		LifecycleManager lifecycleManager = new LifecycleManager() {

			@Override
			public void registerService(Service s) {
			}

			@Override
			public void registerOpenDatabaseHook(OpenDatabaseHook hook,
					Priority p) {
			}

			@Override
			public void registerForShutdown(ExecutorService e) {
			}

			@Override
			public StartResult startServices(SecretKey dbKey) {
				return StartResult.SUCCESS;
			}

			@Override
			public void stopServices() {
			}

			@Override
			public void waitForDatabase() {
			}

			@Override
			public void waitForStartup() {
			}

			@Override
			public void waitForShutdown() {
			}

			@Override
			public LifecycleState getLifecycleState() {
				return RUNNING;
			}
		};
		return lifecycleManager;
	}

	@Provides
	ShutdownManager provideShutdownManager() {
		@NotNullByDefault
		ShutdownManager shutdownManager = new ShutdownManager() {

			@Override
			public int addShutdownHook(Runnable hook) {
				return 0;
			}

			@Override
			public boolean removeShutdownHook(int handle) {
				return true;
			}
		};
		return shutdownManager;
	}

	@Provides
	@IoExecutor
	@Singleton
	Executor provideIoExecutor() {
		return Executors.newCachedThreadPool();
	}
}

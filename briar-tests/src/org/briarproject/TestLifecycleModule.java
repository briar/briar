package org.briarproject;

import org.briarproject.api.clients.Client;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.lifecycle.ShutdownManager;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class TestLifecycleModule {

	@Provides
	LifecycleManager provideLifecycleManager() {
		return new LifecycleManager() {

			@Override
			public void registerService(Service s) {

			}

			@Override
			public void registerClient(Client c) {

			}

			@Override
			public void registerForShutdown(ExecutorService e) {

			}

			@Override
			public StartResult startServices(String authorNick) {
				return StartResult.SUCCESS;
			}

			@Override
			public void stopServices() {

			}

			@Override
			public void waitForDatabase() throws InterruptedException {

			}

			@Override
			public void waitForStartup() throws InterruptedException {

			}

			@Override
			public void waitForShutdown() throws InterruptedException {

			}
		};
	}

	@Provides
	ShutdownManager provideShutdownManager() {
		return new ShutdownManager() {

			@Override
			public int addShutdownHook(Runnable hook) {
				return 0;
			}

			@Override
			public boolean removeShutdownHook(int handle) {
				return true;
			}
		};
	}

	@Provides
	@IoExecutor
	@Singleton
	Executor provideIoExecutor() {
		return Executors.newCachedThreadPool();
	}
}

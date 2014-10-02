package org.briarproject;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.lifecycle.Service;
import org.briarproject.api.lifecycle.ShutdownManager;

import com.google.inject.AbstractModule;

public class TestLifecycleModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(LifecycleManager.class).toInstance(new LifecycleManager() {

			public void register(Service s) {}

			public void registerForShutdown(ExecutorService e) {}

			public StartResult startServices() { return StartResult.SUCCESS; }

			public void stopServices() {}

			public void waitForDatabase() throws InterruptedException {}

			public void waitForStartup() throws InterruptedException {}

			public void waitForShutdown() throws InterruptedException {}
		});
		bind(ShutdownManager.class).toInstance(new ShutdownManager() {

			public int addShutdownHook(Runnable hook) {
				return 0;
			}

			public boolean removeShutdownHook(int handle) {
				return true;
			}
		});
		bind(Executor.class).annotatedWith(IoExecutor.class).toInstance(
				Executors.newCachedThreadPool());
	}
}

package net.sf.briar;

import java.util.concurrent.ExecutorService;

import net.sf.briar.api.lifecycle.LifecycleManager;
import net.sf.briar.api.lifecycle.Service;
import net.sf.briar.api.lifecycle.ShutdownManager;

import com.google.inject.AbstractModule;

public class TestLifecycleModule extends AbstractModule {

	protected void configure() {
		bind(LifecycleManager.class).toInstance(new LifecycleManager() {

			public void register(Service s) {}

			public void registerForShutdown(ExecutorService e) {}

			public void startServices() {}

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
	}
}

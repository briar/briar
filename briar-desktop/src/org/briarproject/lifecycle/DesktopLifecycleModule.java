package org.briarproject.lifecycle;

import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.api.system.Clock;
import org.briarproject.util.OsUtils;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class DesktopLifecycleModule extends LifecycleModule {

	@Provides
	@Singleton
	LifecycleManager provideLifecycleManager(Clock clock, DatabaseComponent db,
			EventBus eventBus) {
		return new LifecycleManagerImpl(clock, db, eventBus);
	}

	@Provides
	@Singleton
	ShutdownManager provideDesktopShutdownManager() {
		if (OsUtils.isWindows()) {
			return new WindowsShutdownManagerImpl();
		}
		else {
			return new ShutdownManagerImpl();
		}
	}

}

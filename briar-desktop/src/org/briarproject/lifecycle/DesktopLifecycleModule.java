package org.briarproject.lifecycle;

import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.util.OsUtils;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class DesktopLifecycleModule extends LifecycleModule {

	@Provides
	@Singleton
	ShutdownManager provideDesktopShutdownManager() {
		if (OsUtils.isWindows()) return new WindowsShutdownManagerImpl();
		else return new ShutdownManagerImpl();
	}
}

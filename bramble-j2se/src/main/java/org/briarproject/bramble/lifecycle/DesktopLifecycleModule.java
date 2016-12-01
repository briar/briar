package org.briarproject.bramble.lifecycle;

import org.briarproject.bramble.api.lifecycle.ShutdownManager;
import org.briarproject.bramble.util.OsUtils;

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

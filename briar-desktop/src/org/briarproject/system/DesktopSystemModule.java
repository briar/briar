package org.briarproject.system;

import org.briarproject.api.system.Clock;
import org.briarproject.api.system.SeedProvider;
import org.briarproject.api.system.Timer;
import org.briarproject.util.OsUtils;

import dagger.Module;
import dagger.Provides;

@Module
public class DesktopSystemModule {

	@Provides
	Clock provideClock() {
		return new SystemClock();
	}

	@Provides
	Timer provideTimer() {
		return new SystemTimer();
	}

	@Provides
	SeedProvider provideSeedProvider() {
		if (OsUtils.isLinux()) {
			return new LinuxSeedProvider();
		}
		return null;
	}
}

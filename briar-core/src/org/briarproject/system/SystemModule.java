package org.briarproject.system;

import org.briarproject.api.system.Clock;
import org.briarproject.api.system.LocationUtils;
import org.briarproject.api.system.SeedProvider;
import org.briarproject.api.system.Timer;

import dagger.Module;
import dagger.Provides;

@Module
public class SystemModule {
	@Provides
	Clock provideClock() {
		return new SystemClock();
	}

	@Provides
	Timer provideTimer() {
		return new SystemTimer();
	}

}

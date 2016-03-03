package org.briarproject.system;


import android.app.Application;

import org.briarproject.api.system.Clock;
import org.briarproject.api.system.LocationUtils;
import org.briarproject.api.system.SeedProvider;
import org.briarproject.api.system.Timer;

import dagger.Module;
import dagger.Provides;

@Module
public class AndroidSystemModule {

	@Provides
	Clock provideClock() {
		return new SystemClock();
	}

	@Provides
	Timer provideTimer() {
		return new SystemTimer();
	}

	@Provides
	SeedProvider provideSeedProvider(final Application app) {
		return new AndroidSeedProvider(app);
	}

	@Provides
	LocationUtils provideLocationUtils(final Application app) {
		return new AndroidLocationUtils(app);
	}

}

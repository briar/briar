package org.briarproject;

import org.briarproject.api.system.Clock;
import org.briarproject.api.system.SeedProvider;
import org.briarproject.api.system.Timer;
import org.briarproject.system.SystemClock;
import org.briarproject.system.SystemTimer;

import dagger.Module;
import dagger.Provides;

@Module
public class TestSystemModule {

	@Provides
	Clock provideClock() {
		return new SystemClock();
	}

	@Provides
	Timer provideSystemTimer() {
		return new SystemTimer();
	}

	@Provides
	SeedProvider provideSeedProvider() {
		return new TestSeedProvider();
	}
}

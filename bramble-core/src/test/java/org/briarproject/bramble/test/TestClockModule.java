package org.briarproject.bramble.test;

import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.system.SystemClock;

import java.util.concurrent.atomic.AtomicLong;

import dagger.Module;
import dagger.Provides;

@Module
public class TestClockModule {

	private final Clock clock;

	public TestClockModule() {
		clock = new SystemClock();
	}

	public TestClockModule(AtomicLong time) {
		clock = new SettableClock(time);
	}

	@Provides
	Clock provideClock() {
		return clock;
	}
}

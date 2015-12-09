package org.briarproject;

import com.google.inject.AbstractModule;

import org.briarproject.api.system.Clock;
import org.briarproject.api.system.SeedProvider;
import org.briarproject.api.system.Timer;
import org.briarproject.system.SystemClock;
import org.briarproject.system.SystemTimer;

public class TestSystemModule extends AbstractModule {

	protected void configure() {
		bind(Clock.class).to(SystemClock.class);
		bind(Timer.class).to(SystemTimer.class);
		bind(SeedProvider.class).to(TestSeedProvider.class);
	}
}

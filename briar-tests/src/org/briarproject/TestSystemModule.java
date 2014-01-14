package org.briarproject;

import org.briarproject.api.system.Clock;
import org.briarproject.api.system.SeedProvider;
import org.briarproject.api.system.Timer;
import org.briarproject.system.SystemClock;
import org.briarproject.system.SystemTimer;

import com.google.inject.AbstractModule;

public class TestSystemModule extends AbstractModule {

	protected void configure() {
		bind(Clock.class).to(SystemClock.class);
		bind(Timer.class).to(SystemTimer.class);
		bind(SeedProvider.class).to(TestSeedProvider.class);
	}
}

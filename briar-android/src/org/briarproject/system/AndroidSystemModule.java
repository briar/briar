package org.briarproject.system;

import com.google.inject.AbstractModule;

import org.briarproject.api.system.Clock;
import org.briarproject.api.system.LocationUtils;
import org.briarproject.api.system.SeedProvider;
import org.briarproject.api.system.Timer;

public class AndroidSystemModule extends AbstractModule {

	protected void configure() {
		bind(Clock.class).to(SystemClock.class);
		bind(Timer.class).to(SystemTimer.class);
		bind(SeedProvider.class).to(AndroidSeedProvider.class);
		bind(LocationUtils.class).to(AndroidLocationUtils.class);
	}
}

package org.briarproject.system;

import org.briarproject.api.system.Clock;
import org.briarproject.api.system.FileUtils;
import org.briarproject.api.system.LocationUtils;
import org.briarproject.api.system.SeedProvider;
import org.briarproject.api.system.Timer;

import com.google.inject.AbstractModule;

public class AndroidSystemModule extends AbstractModule {

	protected void configure() {
		bind(Clock.class).to(SystemClock.class);
		bind(Timer.class).to(SystemTimer.class);
		bind(SeedProvider.class).to(AndroidSeedProvider.class);
		bind(FileUtils.class).to(AndroidFileUtils.class);
		bind(LocationUtils.class).to(AndroidLocationUtils.class);
	}
}

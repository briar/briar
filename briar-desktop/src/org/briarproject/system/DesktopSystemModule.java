package org.briarproject.system;

import com.google.inject.AbstractModule;

import org.briarproject.api.system.Clock;
import org.briarproject.api.system.SeedProvider;
import org.briarproject.api.system.Timer;
import org.briarproject.util.OsUtils;

public class DesktopSystemModule extends AbstractModule {

	protected void configure() {
		bind(Clock.class).to(SystemClock.class);
		bind(Timer.class).to(SystemTimer.class);
		if (OsUtils.isLinux())
			bind(SeedProvider.class).to(LinuxSeedProvider.class);
	}
}

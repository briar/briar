package org.briarproject.system;

import org.briarproject.api.system.Clock;
import org.briarproject.api.system.FileUtils;
import org.briarproject.api.system.SeedProvider;
import org.briarproject.api.system.Timer;
import org.briarproject.util.OsUtils;

import com.google.inject.AbstractModule;

public class DesktopSystemModule extends AbstractModule {

	protected void configure() {
		bind(Clock.class).to(SystemClock.class);
		bind(Timer.class).to(SystemTimer.class);
		if(OsUtils.isLinux())
			bind(SeedProvider.class).to(LinuxSeedProvider.class);
		bind(FileUtils.class).to(DesktopFileUtils.class);
	}
}

package org.briarproject.bramble.system;

import org.briarproject.bramble.api.system.SeedProvider;
import org.briarproject.bramble.util.OsUtils;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class DesktopSeedProviderModule {

	@Provides
	@Singleton
	SeedProvider provideSeedProvider() {
		return OsUtils.isLinux() ? new LinuxSeedProvider() : null;
	}
}

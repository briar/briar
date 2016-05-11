package org.briarproject.system;

import org.briarproject.api.system.SeedProvider;
import org.briarproject.util.OsUtils;

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

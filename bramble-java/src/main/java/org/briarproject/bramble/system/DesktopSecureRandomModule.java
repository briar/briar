package org.briarproject.bramble.system;

import org.briarproject.bramble.api.system.SecureRandomProvider;
import org.briarproject.bramble.util.OsUtils;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class DesktopSecureRandomModule {

	@Provides
	@Singleton
	SecureRandomProvider provideSecureRandomProvider() {
		return OsUtils.isLinux() ? new LinuxSecureRandomProvider() : null;
	}
}

package org.briarproject;

import org.briarproject.api.android.PlatformExecutor;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.ui.UiCallback;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * This class contains methods that MUST(!) be overridden in platform specific
 * modules that use the core.
 */
@Module
public class PlatformModule {

	@Provides
	@Singleton
	public DatabaseConfig provideDatabaseConfig() {
		return null;
	}

	@Provides
	public UiCallback provideUICallback() {
		return null;
	}


	@Provides
	public PlatformExecutor providePlatformExecutor() {
		return null;
	}

}

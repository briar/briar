package org.briarproject.system;

import android.app.Application;

import org.briarproject.android.api.AndroidExecutor;
import org.briarproject.api.system.LocationUtils;
import org.briarproject.api.system.SeedProvider;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class AndroidSystemModule {

	@Provides
	public SeedProvider provideSeedProvider(Application app) {
		return new AndroidSeedProvider(app);
	}

	@Provides
	public LocationUtils provideLocationUtils(Application app) {
		return new AndroidLocationUtils(app);
	}

	@Provides
	@Singleton
	public AndroidExecutor provideAndroidExecutor() {
		return new AndroidExecutorImpl();
	}
}

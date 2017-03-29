package org.briarproject.bramble.system;

import android.app.Application;

import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.SecureRandomProvider;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class AndroidSystemModule {

	@Provides
	@Singleton
	SecureRandomProvider provideSecureRandomProvider(Application app) {
		return new AndroidSecureRandomProvider(app);
	}

	@Provides
	LocationUtils provideLocationUtils(Application app) {
		return new AndroidLocationUtils(app);
	}

	@Provides
	@Singleton
	AndroidExecutor provideAndroidExecutor(Application app) {
		return new AndroidExecutorImpl(app);
	}
}

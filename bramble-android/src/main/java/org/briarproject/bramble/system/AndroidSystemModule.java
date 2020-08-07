package org.briarproject.bramble.system;

import org.briarproject.bramble.api.event.EventExecutor;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.AndroidWakeLockFactory;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;
import org.briarproject.bramble.api.system.SecureRandomProvider;

import java.util.concurrent.Executor;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class AndroidSystemModule {

	@Provides
	@Singleton
	SecureRandomProvider provideSecureRandomProvider(
			AndroidSecureRandomProvider provider) {
		return provider;
	}

	@Provides
	LocationUtils provideLocationUtils(AndroidLocationUtils locationUtils) {
		return locationUtils;
	}

	@Provides
	@Singleton
	AndroidExecutor provideAndroidExecutor(
			AndroidExecutorImpl androidExecutor) {
		return androidExecutor;
	}

	@Provides
	@Singleton
	@EventExecutor
	Executor provideEventExecutor(AndroidExecutor androidExecutor) {
		return androidExecutor::runOnUiThread;
	}

	@Provides
	@Singleton
	ResourceProvider provideResourceProvider(AndroidResourceProvider provider) {
		return provider;
	}

	@Provides
	@Singleton
	AndroidWakeLockFactory provideWakeLockFactory(
			AndroidWakeLockFactoryImpl wakeLockFactory) {
		return wakeLockFactory;
	}
}

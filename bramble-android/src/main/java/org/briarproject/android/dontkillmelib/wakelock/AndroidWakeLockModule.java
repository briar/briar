package org.briarproject.android.dontkillmelib.wakelock;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class AndroidWakeLockModule {

	@Provides
	@Singleton
	AndroidWakeLockManager provideWakeLockManager(
			AndroidWakeLockManagerImpl wakeLockManager) {
		return wakeLockManager;
	}
}

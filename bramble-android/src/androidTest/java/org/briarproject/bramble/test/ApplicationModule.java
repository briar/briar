package org.briarproject.bramble.test;

import android.app.Application;
import android.support.test.InstrumentationRegistry;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
class ApplicationModule {

	@Provides
	@Singleton
	Application provideApplication() {
		return (Application) InstrumentationRegistry.getTargetContext()
						.getApplicationContext();
	}
}

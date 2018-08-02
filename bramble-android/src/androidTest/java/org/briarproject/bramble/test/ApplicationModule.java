package org.briarproject.bramble.test;

import android.app.Application;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static android.support.test.InstrumentationRegistry.getTargetContext;

@Module
class ApplicationModule {

	@Provides
	@Singleton
	Application provideApplication() {
		return (Application) getTargetContext().getApplicationContext();
	}
}

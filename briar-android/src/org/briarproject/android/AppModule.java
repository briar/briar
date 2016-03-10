package org.briarproject.android;

import android.app.Application;

import dagger.Module;
import dagger.Provides;

@Module
public class AppModule {

	Application application;

	public AppModule(Application application) {
		this.application = application;
	}

	@Provides
	@ApplicationScope
	Application providesApplication() {
		return application;
	}
}

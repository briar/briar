package org.briarproject.bramble.plugin.tor.wrapper;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class CircumventionModule {

	@Provides
	@Singleton
	CircumventionProvider provideCircumventionProvider(
			CircumventionProviderImpl provider) {
		return provider;
	}
}

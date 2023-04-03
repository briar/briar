package org.briarproject.onionwrapper;

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

package org.briarproject.bramble.plugin.tor;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class AndroidCircumventionModule {

	@Provides
	@Singleton
	CircumventionProvider provideCircumventionProvider(
			AndroidCircumventionProvider circumventionProvider) {
		return circumventionProvider;
	}
}

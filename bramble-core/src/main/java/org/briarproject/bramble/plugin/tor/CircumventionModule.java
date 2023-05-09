package org.briarproject.bramble.plugin.tor;

import org.briarproject.onionwrapper.CircumventionProvider;
import org.briarproject.onionwrapper.CircumventionProviderFactory;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class CircumventionModule {

	@Provides
	@Singleton
	CircumventionProvider provideCircumventionProvider() {
		return CircumventionProviderFactory.createCircumventionProvider();
	}
}

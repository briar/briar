package org.briarproject.bramble;

import android.app.Application;

import org.briarproject.bramble.plugin.tor.CircumventionProvider;
import org.briarproject.bramble.plugin.tor.CircumventionProviderImpl;
import org.briarproject.bramble.system.AndroidSystemModule;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module(includes = {
		AndroidSystemModule.class
})
public class BrambleAndroidModule {

	@Provides
	@Singleton
	CircumventionProvider provideCircumventionProvider(Application app) {
		return new CircumventionProviderImpl(app.getApplicationContext());
	}

}

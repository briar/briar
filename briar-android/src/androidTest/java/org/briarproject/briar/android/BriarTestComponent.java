package org.briarproject.briar.android;

import org.briarproject.bramble.BrambleAndroidModule;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.briar.BriarCoreModule;
import org.briarproject.briar.android.settings.DarkThemeTest;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		AppModule.class,
		BriarCoreModule.class,
		BrambleAndroidModule.class,
		BrambleCoreModule.class
})
public interface BriarTestComponent extends AndroidComponent {

	void inject(DarkThemeTest test);

}

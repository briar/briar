package org.briarproject.briar.android;

import org.briarproject.bramble.BrambleAndroidModule;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.account.BriarAccountModule;
import org.briarproject.briar.BriarCoreModule;
import org.briarproject.briar.android.login.PasswordActivityTest;
import org.briarproject.briar.android.login.SetupActivityTest;
import org.briarproject.briar.android.navdrawer.NavDrawerActivityTest;
import org.briarproject.briar.android.settings.SettingsActivityTest;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		AppModule.class,
		BriarCoreModule.class,
		BrambleAndroidModule.class,
		BriarAccountModule.class,
		BrambleCoreModule.class
})
public interface TestComponent extends AndroidComponent {

	void inject(SetupActivityTest test);
	void inject(PasswordActivityTest test);
	void inject(NavDrawerActivityTest test);
	void inject(SettingsActivityTest test);

}

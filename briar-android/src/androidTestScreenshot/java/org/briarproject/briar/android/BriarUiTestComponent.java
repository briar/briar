package org.briarproject.briar.android;

import org.briarproject.bramble.BrambleAndroidModule;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.account.BriarAccountModule;
import org.briarproject.briar.BriarCoreModule;
import org.briarproject.briar.android.contact.ConversationActivityScreenshotTest;
import org.briarproject.briar.android.login.SetupActivityScreenshotTest;
import org.briarproject.briar.android.settings.SettingsActivityScreenshotTest;

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
public interface BriarUiTestComponent extends AndroidComponent {

	void inject(ConversationActivityScreenshotTest test);
	void inject(SetupActivityScreenshotTest test);
	void inject(SettingsActivityScreenshotTest test);

}

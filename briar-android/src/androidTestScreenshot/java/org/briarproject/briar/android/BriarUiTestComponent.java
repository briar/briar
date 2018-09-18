package org.briarproject.briar.android;

import org.briarproject.bramble.BrambleAndroidModule;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.account.BriarAccountModule;
import org.briarproject.briar.BriarCoreModule;
import org.briarproject.briar.android.contact.ConversationActivityScreenshotTest;
import org.briarproject.briar.android.settings.SettingsActivityScreenshotTest;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		TestAppModule.class,
		BriarCoreModule.class,
		BrambleAndroidModule.class,
		BriarAccountModule.class,
		BrambleCoreModule.class
})
public interface BriarUiTestComponent extends AndroidComponent, FakeDataTestComponent {

	void inject(SetupDataTest test);

	void inject(ConversationActivityScreenshotTest test);
	void inject(SettingsActivityScreenshotTest test);

}

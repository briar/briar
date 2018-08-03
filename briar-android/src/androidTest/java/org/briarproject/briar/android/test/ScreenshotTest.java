package org.briarproject.briar.android.test;

import android.app.Activity;
import android.support.test.espresso.intent.rule.IntentsTestRule;
import android.util.Log;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.briar.android.BriarTestComponentApplication;
import org.briarproject.briar.android.BriarUiTestComponent;
import org.junit.ClassRule;

import javax.inject.Inject;

import tools.fastlane.screengrab.Screengrab;
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy;
import tools.fastlane.screengrab.locale.LocaleTestRule;

import static android.support.test.InstrumentationRegistry.getTargetContext;
import static tools.fastlane.screengrab.Screengrab.setDefaultScreenshotStrategy;

public abstract class ScreenshotTest {

	@ClassRule
	public static final LocaleTestRule localeTestRule = new LocaleTestRule();

	protected static final String USERNAME = "Alice";
	protected static final String PASSWORD = "123456";

	@Inject
	protected AccountManager accountManager;
	@Inject
	protected LifecycleManager lifecycleManager;

	public ScreenshotTest() {
		super();
		setDefaultScreenshotStrategy(new UiAutomatorScreenshotStrategy());
		BriarTestComponentApplication app =
				(BriarTestComponentApplication) getTargetContext()
						.getApplicationContext();
		inject((BriarUiTestComponent) app.getApplicationComponent());
	}

	protected abstract void inject(BriarUiTestComponent component);

	protected void screenshot(String name) {
		try {
			Screengrab.screenshot(name);
		} catch (RuntimeException e) {
			if (!e.getMessage().equals("Unable to capture screenshot."))
				throw e;
			// The tests should still pass when run from AndroidStudio
			// without manually granting permissions like fastlane does.
			Log.w("Screengrab", "Permission to write screenshot is missing.");
		}
	}

	protected class CleanAccountTestRule<A extends Activity>
			extends IntentsTestRule<A> {

		public CleanAccountTestRule(Class<A> activityClass) {
			super(activityClass);
		}

		@Override
		protected void beforeActivityLaunched() {
			super.beforeActivityLaunched();
			accountManager.deleteAccount();
			accountManager.createAccount(USERNAME, PASSWORD);
		}
	}

}

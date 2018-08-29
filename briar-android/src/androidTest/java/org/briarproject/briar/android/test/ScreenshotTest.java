package org.briarproject.briar.android.test;

import android.app.Activity;
import android.content.Intent;
import android.support.test.espresso.intent.rule.IntentsTestRule;
import android.util.Log;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.android.BriarService;
import org.briarproject.briar.android.BriarTestComponentApplication;
import org.briarproject.briar.android.BriarUiTestComponent;
import org.briarproject.briar.api.test.TestDataCreator;
import org.junit.ClassRule;

import javax.annotation.Nullable;
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
	@Inject
	protected TestDataCreator testDataCreator;
	@Inject
	protected ConnectionRegistry connectionRegistry;
	@Inject
	protected Clock clock;

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

	protected long getMinutesAgo(int minutes) {
		return clock.currentTimeMillis() - minutes * 60 * 1000;
	}

	@NotNullByDefault
	protected class CleanAccountTestRule<A extends Activity>
			extends IntentsTestRule<A> {

		@Nullable
		private final Runnable runnable;

		public CleanAccountTestRule(Class<A> activityClass) {
			super(activityClass);
			this.runnable = null;
		}

		/**
		 * Use this if you need to run code before launching the activity.
		 * Note: You need to use {@link #launchActivity(Intent)} yourself
		 * to start the activity.
		 */
		public CleanAccountTestRule(Class<A> activityClass, Runnable runnable) {
			super(activityClass, false, false);
			this.runnable = runnable;
		}

		@Override
		protected void beforeActivityLaunched() {
			super.beforeActivityLaunched();
			accountManager.deleteAccount();
			accountManager.createAccount(USERNAME, PASSWORD);
			if (runnable != null) {
				Intent serviceIntent =
						new Intent(getTargetContext(), BriarService.class);
				getTargetContext().startService(serviceIntent);
				try {
					lifecycleManager.waitForStartup();
				} catch (InterruptedException e) {
					throw new AssertionError(e);
				}
				runnable.run();
			}
		}
	}

}

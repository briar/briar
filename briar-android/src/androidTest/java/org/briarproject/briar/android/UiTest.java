package org.briarproject.briar.android;

import android.app.Activity;
import android.content.Intent;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.briar.R;

import javax.inject.Inject;

import androidx.test.espresso.intent.rule.IntentsTestRule;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.briarproject.briar.android.controller.BriarControllerImpl.DOZE_ASK_AGAIN;
import static org.briarproject.briar.android.settings.SettingsFragment.SETTINGS_NAMESPACE;


@SuppressWarnings("WeakerAccess")
public abstract class UiTest {

	protected final String USERNAME =
			getApplicationContext().getString(R.string.screenshot_alice);
	protected static final String PASSWORD = "123456";

	@Inject
	protected AccountManager accountManager;
	@Inject
	protected LifecycleManager lifecycleManager;
	@Inject
	protected SettingsManager settingsManager;

	public UiTest() {
		BriarTestComponentApplication app = getApplicationContext();
		inject((BriarUiTestComponent) app.getApplicationComponent());
	}

	protected abstract void inject(BriarUiTestComponent component);

	@NotNullByDefault
	protected class CleanAccountTestRule<A extends Activity>
			extends IntentsTestRule<A> {

		public CleanAccountTestRule(Class<A> activityClass) {
			super(activityClass);
		}

		@Override
		protected void beforeActivityLaunched() {
			super.beforeActivityLaunched();
			// Android Test Orchestrator already clears existing accounts
			accountManager.createAccount(USERNAME, PASSWORD);
			Intent serviceIntent =
					new Intent(getApplicationContext(), BriarService.class);
			getApplicationContext().startService(serviceIntent);
			try {
				lifecycleManager.waitForStartup();
				// do not show doze white-listing dialog
				Settings settings = new Settings();
				settings.putBoolean(DOZE_ASK_AGAIN, false);
				settingsManager.mergeSettings(settings, SETTINGS_NAMESPACE);
			} catch (InterruptedException | DbException e) {
				throw new AssertionError(e);
			}
		}
	}

}

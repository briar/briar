package org.briarproject.briar.android;

import android.app.Activity;
import android.content.Intent;
import android.support.test.espresso.intent.rule.IntentsTestRule;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.support.test.InstrumentationRegistry.getTargetContext;

@SuppressWarnings("WeakerAccess")
public abstract class UiTest {

	protected final String USERNAME =
			getTargetContext().getString(R.string.screenshot_alice);
	protected static final String PASSWORD = "123456";

	@Inject
	protected AccountManager accountManager;
	@Inject
	protected LifecycleManager lifecycleManager;

	public UiTest() {
		BriarTestComponentApplication app =
				(BriarTestComponentApplication) getTargetContext()
						.getApplicationContext();
		inject((BriarUiTestComponent) app.getApplicationComponent());
	}

	protected abstract void inject(BriarUiTestComponent component);

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

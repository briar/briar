package org.briarproject.briar.android.test;

import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.NoMatchingViewException;
import android.support.test.espresso.contrib.DrawerActions;
import android.util.Log;
import android.view.Gravity;

import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.briar.R;
import org.briarproject.briar.android.BriarTestApplication;
import org.briarproject.briar.android.BriarTestComponent;
import org.junit.Before;
import org.junit.ClassRule;

import javax.inject.Inject;

import tools.fastlane.screengrab.Screengrab;
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy;
import tools.fastlane.screengrab.locale.LocaleTestRule;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.DrawerMatchers.isClosed;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.RUNNING;
import static tools.fastlane.screengrab.Screengrab.setDefaultScreenshotStrategy;

public abstract class ScreenshotTest {

	@ClassRule
	public static final LocaleTestRule localeTestRule = new LocaleTestRule();

	@Before
	public void setupScreenshots() {
		setDefaultScreenshotStrategy(new UiAutomatorScreenshotStrategy());
	}

	private static final String USERNAME = "test";
	private static final String PASSWORD = "123456";

	private final BriarTestApplication app =
			(BriarTestApplication) InstrumentationRegistry.getTargetContext()
					.getApplicationContext();
	@Inject
	LifecycleManager lifecycleManager;

	protected abstract void inject(BriarTestComponent component);

	/**
	 * Signs the user in.
	 *
	 * Note that you need to wait for your UI to show up after this.
	 * See {@link ViewActions#waitForActivityToResume} for one way to do it.
	 */
	@Before
	public void signIn() throws Exception {
		inject((BriarTestComponent) app.getApplicationComponent());
		if (lifecycleManager.getLifecycleState() == RUNNING) return;

		try {
			onView(withId(R.id.edit_password))
					.check(matches(isDisplayed()))
					.perform(typeText(PASSWORD));
			onView(withId(R.id.btn_sign_in))
					.check(matches(isDisplayed()))
					.perform(click());
		} catch (NoMatchingViewException e) {
			// we start from a blank state and have no account, yet
			createAccount();
		}
	}

	private void createAccount() {
		// TODO use AccountManager to start with fresh account
		// TODO move this below into a dedicated test for SetupActivity

		// Enter username
		onView(withText(R.string.setup_title))
				.check(matches(isDisplayed()));
		onView(withId(R.id.nickname_entry))
				.check(matches(isDisplayed()))
				.perform(typeText(USERNAME));
		onView(withId(R.id.next))
				.check(matches(isDisplayed()))
				.perform(click());

		// Enter password
		onView(withId(R.id.password_entry))
				.check(matches(isDisplayed()))
				.perform(typeText(PASSWORD));
		onView(withId(R.id.password_confirm))
				.check(matches(isDisplayed()))
				.perform(typeText(PASSWORD));
		onView(withId(R.id.next))
				.check(matches(isDisplayed()))
				.perform(click());
		onView(withId(R.id.progress))
				.check(matches(isDisplayed()));
	}

	protected void signOut() {
		onView(withId(R.id.drawer_layout))
				.check(matches(isClosed(Gravity.LEFT)))
				.perform(DrawerActions.open());
		onView(withText(R.string.sign_out_button))
				.check(matches(isDisplayed()))
				.perform(click());
	}

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

}

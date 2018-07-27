package org.briarproject.briar.android.test;

import android.app.Activity;
import android.support.test.espresso.NoMatchingViewException;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiSelector;
import android.util.Log;

import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.briar.R;
import org.briarproject.briar.android.TestBriarApplication;
import org.briarproject.briar.android.TestComponent;
import org.junit.Before;
import org.junit.ClassRule;

import javax.inject.Inject;

import tools.fastlane.screengrab.Screengrab;
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy;
import tools.fastlane.screengrab.locale.LocaleTestRule;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.support.test.InstrumentationRegistry.getTargetContext;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.isRoot;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.RUNNING;
import static org.briarproject.briar.android.test.ViewActions.waitForActivityToResume;
import static org.briarproject.briar.android.util.UiUtils.needsDozeWhitelisting;
import static tools.fastlane.screengrab.Screengrab.setDefaultScreenshotStrategy;

public abstract class ScreenshotTest {

	@ClassRule
	public static final LocaleTestRule localeTestRule = new LocaleTestRule();

	private static final String USERNAME = "test";
	private static final String PASSWORD = "123456";

	private final TestBriarApplication app =
			(TestBriarApplication) getTargetContext()
					.getApplicationContext();
	@Inject
	LifecycleManager lifecycleManager;

	protected abstract void inject(TestComponent component);
	protected abstract Activity getActivity();

	@Before
	public void setupScreenshots() {
		setDefaultScreenshotStrategy(new UiAutomatorScreenshotStrategy());
	}

	@Before
	public void signIn() throws Exception {
		inject((TestComponent) app.getApplicationComponent());
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
		onView(isRoot())
				.perform(waitForActivityToResume(getActivity()));
	}

	private void createAccount() throws Exception {
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

		// White-list Doze if needed
		if (needsDozeWhitelisting(getTargetContext())) {
			onView(withText(R.string.setup_doze_button))
					.check(matches(isDisplayed()))
					.perform(click());
			UiDevice device = UiDevice.getInstance(getInstrumentation());
			UiObject allowButton = device.findObject(
					new UiSelector().className("android.widget.Button")
							.index(1));
			allowButton.click();
			onView(withId(R.id.next))
					.check(matches(isDisplayed()))
					.perform(click());
		}

		onView(withId(R.id.progress))
				.check(matches(isDisplayed()));
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

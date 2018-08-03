package org.briarproject.briar.android.login;

import android.support.test.espresso.intent.rule.IntentsTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiSelector;

import org.briarproject.briar.R;
import org.briarproject.briar.android.BriarUiTestComponent;
import org.briarproject.briar.android.test.ScreenshotTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.support.test.InstrumentationRegistry.getTargetContext;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.intent.Intents.intended;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.isRoot;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static android.support.test.runner.lifecycle.Stage.PAUSED;
import static junit.framework.Assert.assertTrue;
import static org.briarproject.briar.android.test.ViewActions.waitForActivity;
import static org.briarproject.briar.android.test.ViewActions.waitUntilMatches;
import static org.briarproject.briar.android.util.UiUtils.needsDozeWhitelisting;


@RunWith(AndroidJUnit4.class)
public class SetupActivityScreenshotTest extends ScreenshotTest {

	@Rule
	public IntentsTestRule<SetupActivity> testRule =
			new IntentsTestRule<SetupActivity>(SetupActivity.class) {
				@Override
				protected void beforeActivityLaunched() {
					super.beforeActivityLaunched();
					accountManager.deleteAccount();
				}
			};

	@Override
	protected void inject(BriarUiTestComponent component) {
		component.inject(this);
	}

	@Test
	public void createAccount() throws Exception {
		// Enter username
		onView(withText(R.string.setup_title))
				.check(matches(isDisplayed()));
		onView(withId(R.id.nickname_entry))
				.check(matches(isDisplayed()))
				.perform(typeText(USERNAME));
		onView(withId(R.id.nickname_entry))
				.perform(waitUntilMatches(withText(USERNAME)));

		screenshot("manual_create_account");

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

		// wait for OpenDatabaseActivity to show up
		onView(withId(R.id.progress))
				.check(matches(isDisplayed()));
		onView(isRoot())
				.perform(waitForActivity(testRule.getActivity(), PAUSED));
		intended(hasComponent(OpenDatabaseActivity.class.getName()));

		assertTrue(accountManager.hasDatabaseKey());
	}

}

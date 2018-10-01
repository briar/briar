package org.briarproject.briar.android;

import android.support.test.espresso.intent.rule.IntentsTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiSelector;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.R;
import org.briarproject.briar.android.login.OpenDatabaseActivity;
import org.briarproject.briar.android.login.SetupActivity;
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
import static org.briarproject.bramble.api.plugin.LanTcpConstants.ID;
import static org.briarproject.briar.android.ViewActions.waitForActivity;
import static org.briarproject.briar.android.ViewActions.waitUntilMatches;
import static org.briarproject.briar.android.util.UiUtils.needsDozeWhitelisting;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class SetupDataTest extends ScreenshotTest {

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

		screenshot("manual_create_account", testRule.getActivity());

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
		onView(isRoot())
				.perform(waitForActivity(testRule.getActivity(), PAUSED));
		intended(hasComponent(OpenDatabaseActivity.class.getName()));

		assertTrue(accountManager.hasDatabaseKey());

		lifecycleManager.waitForStartup();
		createTestData();

		// close expiry warning
		onView(withId(R.id.expiryWarning))
				.perform(waitUntilMatches(isDisplayed()));
		onView(withId(R.id.expiryWarningClose))
				.check(matches(isDisplayed()));
		onView(withId(R.id.expiryWarningClose))
				.perform(click());
	}

	private void createTestData() {
		try {
			createTestDataExceptions();
		} catch (DbException | FormatException e) {
			throw new AssertionError(e);
		}
	}

	private void createTestDataExceptions()
			throws DbException, FormatException {
		String bobName =
				getTargetContext().getString(R.string.screenshot_bob);
		Contact bob = testDataCreator.addContact(bobName);

		String bobHi = getTargetContext()
				.getString(R.string.screenshot_message_1);
		long bobTime = getMinutesAgo(2);
		testDataCreator.addPrivateMessage(bob, bobHi, bobTime, true);

		String aliceHi = getTargetContext()
				.getString(R.string.screenshot_message_2);
		long aliceTime = getMinutesAgo(1);
		testDataCreator.addPrivateMessage(bob, aliceHi, aliceTime, false);

		String bobHi2 = getTargetContext()
				.getString(R.string.screenshot_message_3);
		long bobTime2 = getMinutesAgo(0);
		testDataCreator.addPrivateMessage(bob, bobHi2, bobTime2, true);

		connectionRegistry.registerConnection(bob.getId(), ID, true);
	}

}

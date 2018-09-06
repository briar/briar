package org.briarproject.briar.android.contact;

import android.content.Context;
import android.content.Intent;
import android.support.test.runner.AndroidJUnit4;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.R;
import org.briarproject.briar.android.BriarUiTestComponent;
import org.briarproject.briar.android.ScreenshotTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.support.test.InstrumentationRegistry.getTargetContext;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.briarproject.bramble.api.plugin.LanTcpConstants.ID;
import static org.briarproject.briar.android.contact.ConversationActivity.CONTACT_ID;
import static org.briarproject.briar.android.ViewActions.waitUntilMatches;
import static org.hamcrest.Matchers.allOf;

@RunWith(AndroidJUnit4.class)
public class ConversationActivityScreenshotTest extends ScreenshotTest {

	@Rule
	public CleanAccountTestRule<ConversationActivity> testRule =
			new CleanAccountTestRule<>(ConversationActivity.class,
					this::createTestData);

	@Override
	protected void inject(BriarUiTestComponent component) {
		component.inject(this);
	}

	@Test
	public void messaging() {
		Context targetContext = getInstrumentation().getTargetContext();
		Intent intent = new Intent(targetContext, ConversationActivity.class);
		intent.putExtra(CONTACT_ID, 1);
		testRule.launchActivity(intent);

		onView(withId(R.id.conversationView)).perform(waitUntilMatches(
				allOf(withText(R.string.screenshot_message_3), isDisplayed())));

		screenshot("manual_messaging");
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

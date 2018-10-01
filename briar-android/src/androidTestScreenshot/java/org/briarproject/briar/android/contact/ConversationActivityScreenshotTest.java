package org.briarproject.briar.android.contact;

import android.content.Context;
import android.content.Intent;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.briarproject.briar.R;
import org.briarproject.briar.android.BriarUiTestComponent;
import org.briarproject.briar.android.ScreenshotTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.briarproject.briar.android.ViewActions.waitUntilMatches;
import static org.briarproject.briar.android.contact.ConversationActivity.CONTACT_ID;
import static org.hamcrest.Matchers.allOf;

@RunWith(AndroidJUnit4.class)
public class ConversationActivityScreenshotTest extends ScreenshotTest {

	@Rule
	public ActivityTestRule<ConversationActivity> testRule =
			new ActivityTestRule<>(ConversationActivity.class, false, false);

	@Override
	protected void inject(BriarUiTestComponent component) {
		component.inject(this);
	}

	@Test
	public void messaging() throws Exception {
		Context targetContext = getInstrumentation().getTargetContext();
		Intent intent = new Intent(targetContext, ConversationActivity.class);
		intent.putExtra(CONTACT_ID, 1);
		testRule.launchActivity(intent);

		onView(withId(R.id.conversationView))
				.perform(waitUntilMatches(allOf(
						withText(R.string.screenshot_message_3),
						isCompletelyDisplayed())
				));

		screenshot("manual_messaging", testRule.getActivity());
	}

}

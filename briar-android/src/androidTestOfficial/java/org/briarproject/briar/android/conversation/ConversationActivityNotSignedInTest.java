package org.briarproject.briar.android.conversation;

import android.content.Context;
import android.content.Intent;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.briarproject.briar.R;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.briarproject.briar.android.ViewActions.waitUntilMatches;
import static org.briarproject.briar.android.conversation.ConversationActivity.CONTACT_ID;

@RunWith(AndroidJUnit4.class)
public class ConversationActivityNotSignedInTest {

	@Rule
	public ActivityTestRule<ConversationActivity> testRule =
			new ActivityTestRule<>(ConversationActivity.class, false, false);

	@Test
	public void openWithoutSignedIn() {
		Context targetContext = getInstrumentation().getTargetContext();
		Intent intent = new Intent(targetContext, ConversationActivity.class);
		intent.putExtra(CONTACT_ID, 1);
		testRule.launchActivity(intent);

		onView(withText(R.string.sign_in_button))
				.perform(waitUntilMatches(isDisplayed()));
	}

}

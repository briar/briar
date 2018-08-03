package org.briarproject.briar.android.login;

import android.support.test.runner.AndroidJUnit4;

import org.briarproject.briar.R;
import org.briarproject.briar.android.BriarUiTestComponent;
import org.briarproject.briar.android.test.ScreenshotTest;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.intent.Intents.intended;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;


@RunWith(AndroidJUnit4.class)
public class PasswordActivityTest extends ScreenshotTest {

	@Rule
	public CleanAccountTestRule<PasswordActivity> testRule =
			new CleanAccountTestRule<>(PasswordActivity.class);

	@Override
	protected void inject(BriarUiTestComponent component) {
		component.inject(this);
	}

	// FIXME
	@Ignore("Need to find a way to sign-out after creating fresh account")
	@Test
	public void successfulLogin() {
		onView(withId(R.id.edit_password))
				.check(matches(isDisplayed()))
				.perform(typeText(PASSWORD));
		onView(withId(R.id.btn_sign_in))
				.check(matches(isDisplayed()))
				.perform(click());
		onView(withId(R.id.progress))
				.check(matches(isDisplayed()));
		intended(hasComponent(OpenDatabaseActivity.class.getName()));
	}

}

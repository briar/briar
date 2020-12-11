package org.briarproject.briar.android.account;

import android.view.View;

import org.briarproject.briar.R;
import org.briarproject.briar.android.login.StrengthMeter;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import androidx.test.espresso.matcher.BoundedMatcher;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.NONE;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.QUITE_STRONG;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.QUITE_WEAK;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.STRONG;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.WEAK;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.android.login.StrengthMeter.GREEN;
import static org.briarproject.briar.android.login.StrengthMeter.LIME;
import static org.briarproject.briar.android.login.StrengthMeter.ORANGE;
import static org.briarproject.briar.android.login.StrengthMeter.RED;
import static org.briarproject.briar.android.login.StrengthMeter.YELLOW;
import static org.hamcrest.Matchers.not;

@RunWith(AndroidJUnit4.class)
@Config(sdk = 21)
public class SetupActivityTest {

	@Rule
	public ActivityScenarioRule<SetupActivity> rule =
			new ActivityScenarioRule<>(SetupActivity.class);

	@Test
	public void testNicknameTooLongErrorShown() {
		String longNick = getRandomString(MAX_AUTHOR_NAME_LENGTH + 1);
		onView(withId(R.id.nickname_entry)).perform(typeText(longNick));

		// Nickname should be too long
		onView(withText(R.string.name_too_long)).check(matches(isDisplayed()));
	}

	@Test
	public void testPasswordMatchUI() {
		moveToSetPasswordFragment();

		// error shown when passwords don't match, button is disabled
		onView(withId(R.id.password_entry)).perform(typeText("123456"));
		onView(withId(R.id.password_confirm)).perform(typeText("654321"));
		onView(withText(R.string.passwords_do_not_match))
				.check(matches(isDisplayed()));
		onView(withId(R.id.next)).check(matches(not(isEnabled())));

		// confirming correct password, removes error, enables button
		onView(withId(R.id.password_confirm)).perform(clearText());
		onView(withId(R.id.password_confirm)).perform(replaceText("123456"));
		onView(withText(R.string.passwords_do_not_match)).check(doesNotExist());
		onView(withId(R.id.next)).check(matches(isEnabled()));

		// clicking the button shows progress bar, no doze because SDK_INT==21
		onView(withId(R.id.next)).perform(click());
		onView(withId(R.id.progress)).check(matches(isDisplayed()));
	}

	@Test
	public void testStrengthMeterUI() {
		moveToSetPasswordFragment();

		onView(withId(R.id.password_entry)).perform(typeText("1234567890ab"));
		onView(withId(R.id.strength_meter))
				.check(matches(strengthAndColor(STRONG, GREEN)));

		onView(withId(R.id.password_entry)).perform(clearText());
		onView(withId(R.id.password_entry)).perform(typeText("123456789"));
		onView(withId(R.id.strength_meter))
				.check(matches(strengthAndColor(QUITE_STRONG, LIME)));

		onView(withId(R.id.password_entry)).perform(clearText());
		onView(withId(R.id.password_entry)).perform(typeText("123456"));
		onView(withId(R.id.strength_meter))
				.check(matches(strengthAndColor(QUITE_WEAK, YELLOW)));

		onView(withId(R.id.password_entry)).perform(clearText());
		onView(withId(R.id.password_entry)).perform(typeText("123"));
		onView(withId(R.id.strength_meter))
				.check(matches(strengthAndColor(WEAK, ORANGE)));

		onView(withId(R.id.password_entry)).perform(clearText());
		onView(withId(R.id.strength_meter))
				.check(matches(strengthAndColor(NONE, RED)));
	}

	private void moveToSetPasswordFragment() {
		onView(withId(R.id.nickname_entry)).perform(typeText("test"));
		onView(withId(R.id.next)).perform(click());
		onView(withId(R.id.password_entry)).check(matches(isDisplayed()));
	}

	private Matcher<View> strengthAndColor(float strength, int color) {
		return new StrengthMeterMatcher(strength, color);
	}

	static class StrengthMeterMatcher
			extends BoundedMatcher<View, StrengthMeter> {

		private final float strength;
		private final int color;

		private StrengthMeterMatcher(float strength, int color) {
			super(StrengthMeter.class);
			this.strength = strength;
			this.color = color;
		}

		@Override
		public void describeTo(Description description) {
			description.appendText("is enabled");
		}

		@Override
		public boolean matchesSafely(StrengthMeter view) {
			boolean strengthMatches =
					view.getProgress() == (int) (view.getMax() * strength);
			boolean colorMatches = color == view.getColor();
			return strengthMatches && colorMatches;
		}
	}

}

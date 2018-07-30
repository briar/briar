package org.briarproject.briar.android.settings;

import android.content.Intent;
import android.support.test.espresso.contrib.DrawerActions;
import android.support.test.runner.AndroidJUnit4;
import android.view.Gravity;

import org.briarproject.briar.R;
import org.briarproject.briar.android.TestComponent;
import org.briarproject.briar.android.navdrawer.NavDrawerActivity;
import org.briarproject.briar.android.test.ScreenshotTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.DrawerMatchers.isClosed;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

@RunWith(AndroidJUnit4.class)
public class SettingsActivityTest extends ScreenshotTest {

	@Rule
	public CleanAccountTestRule<SettingsActivity> testRule =
			new CleanAccountTestRule<>(SettingsActivity.class);

	@Override
	protected void inject(TestComponent component) {
		component.inject(this);
	}

	@Test
	public void changeTheme() {
		onView(withText(R.string.settings_button))
				.check(matches(isDisplayed()));

		screenshot("manual_dark_theme_settings");

		// switch to dark theme
		onView(withText(R.string.pref_theme_title))
				.check(matches(isDisplayed()))
				.perform(click());
		onView(withText(R.string.pref_theme_dark))
				.check(matches(isDisplayed()))
				.perform(click());

		// start main activity
		Intent i =
				new Intent(testRule.getActivity(), NavDrawerActivity.class);
		testRule.getActivity().startActivity(i);

		// close expiry warning
		onView(withId(R.id.expiryWarningClose))
				.check(matches(isDisplayed()));
		onView(withId(R.id.expiryWarningClose))
				.perform(click());

		// open navigation drawer
		onView(withId(R.id.drawer_layout))
				.check(matches(isClosed(Gravity.START)))
				.perform(DrawerActions.open());

		screenshot("manual_dark_theme_nav_drawer");
	}

}

package org.briarproject.briar.android.navdrawer;

import android.support.test.espresso.contrib.DrawerActions;
import android.support.test.espresso.intent.rule.IntentsTestRule;
import android.view.Gravity;

import org.briarproject.briar.R;
import org.briarproject.briar.android.BriarTestComponent;
import org.briarproject.briar.android.settings.SettingsActivity;
import org.briarproject.briar.android.test.ScreenshotTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.DrawerMatchers.isClosed;
import static android.support.test.espresso.intent.Intents.intended;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.isRoot;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.briarproject.briar.android.test.ViewActions.waitForActivityToResume;

public class NavDrawerActivityTest extends ScreenshotTest {

	@Rule
	public IntentsTestRule<NavDrawerActivity> activityRule =
			new IntentsTestRule<>(NavDrawerActivity.class);

	@Override
	protected void inject(BriarTestComponent component) {
		component.inject(this);
	}

	@Before
	public void waitForSignIn() {
		onView(isRoot())
				.perform(waitForActivityToResume(activityRule.getActivity()));
	}

	@Test
	public void openSettings() {
		onView(withId(R.id.drawer_layout))
				.check(matches(isClosed(Gravity.LEFT)))
				.perform(DrawerActions.open());
		onView(withText(R.string.settings_button))
				.check(matches(isDisplayed()))
				.perform(click());
		intended(hasComponent(SettingsActivity.class.getName()));
	}

}

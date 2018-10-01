package org.briarproject.briar.android.navdrawer;

import android.support.test.espresso.contrib.DrawerActions;
import android.support.test.runner.AndroidJUnit4;
import android.view.Gravity;

import org.briarproject.briar.R;
import org.briarproject.briar.android.BriarUiTestComponent;
import org.briarproject.briar.android.UiTest;
import org.briarproject.briar.android.settings.SettingsActivity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.DrawerMatchers.isClosed;
import static android.support.test.espresso.intent.Intents.intended;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

@RunWith(AndroidJUnit4.class)
public class NavDrawerActivityTest extends UiTest {

	@Rule
	public CleanAccountTestRule<NavDrawerActivity> testRule =
			new CleanAccountTestRule<>(NavDrawerActivity.class);

	@Override
	protected void inject(BriarUiTestComponent component) {
		component.inject(this);
	}

	@Test
	public void openSettings() {
		onView(withId(R.id.drawer_layout))
				.check(matches(isClosed(Gravity.START)))
				.perform(DrawerActions.open());
		onView(withText(R.string.settings_button))
				.check(matches(isDisplayed()))
				.perform(click());
		intended(hasComponent(SettingsActivity.class.getName()));
	}

}

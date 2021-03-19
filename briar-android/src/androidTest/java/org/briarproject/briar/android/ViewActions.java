package org.briarproject.briar.android;

import android.app.Activity;
import android.view.View;

import org.hamcrest.Matcher;

import java.util.concurrent.TimeoutException;

import androidx.test.espresso.PerformException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitor;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.util.HumanReadables.describe;
import static androidx.test.espresso.util.TreeIterables.breadthFirstViewTraversal;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ViewActions {

	private final static long TIMEOUT_MS = SECONDS.toMillis(10);
	private final static long WAIT_MS = 50;

	public static void waitFor(final Matcher<View> viewMatcher) {
		onView(isRoot()).perform(waitUntilMatches(hasDescendant(viewMatcher)));
	}

	public static ViewAction waitUntilMatches(Matcher<View> viewMatcher) {
		return waitUntilMatches(viewMatcher, TIMEOUT_MS);
	}

	private static ViewAction waitUntilMatches(Matcher<View> viewMatcher,
			long timeout) {
		return new CustomViewAction() {
			@Override
			protected boolean exitConditionTrue(View view) {
				for (View child : breadthFirstViewTraversal(view)) {
					if (viewMatcher.matches(child)) return true;
				}
				return false;
			}

			@Override
			public String getDescription() {
				return "Wait for view matcher " + viewMatcher +
						" to match within " + timeout + " milliseconds.";
			}
		};
	}

	public static ViewAction waitForActivity(Activity activity, Stage stage) {
		return new CustomViewAction() {
			@Override
			protected boolean exitConditionTrue(View view) {
				ActivityLifecycleMonitor lifecycleMonitor =
						ActivityLifecycleMonitorRegistry.getInstance();
				return lifecycleMonitor.getLifecycleStageOf(activity) == stage;
			}

			@Override
			public String getDescription() {
				return "Wait for activity " + activity.getClass().getName() +
						" to resume within " + TIMEOUT_MS + " milliseconds.";
			}
		};
	}

	private static abstract class CustomViewAction implements ViewAction {
		@Override
		public Matcher<View> getConstraints() {
			return isDisplayed();
		}

		@Override
		public void perform(UiController uiController, View view) {
			uiController.loopMainThreadUntilIdle();
			long endTime = currentTimeMillis() + TIMEOUT_MS;
			do {
				if (exitConditionTrue(view)) return;
				uiController.loopMainThreadForAtLeast(WAIT_MS);
			}
			while (currentTimeMillis() < endTime);

			throw new PerformException.Builder()
					.withActionDescription(getDescription())
					.withViewDescription(describe(view))
					.withCause(new TimeoutException())
					.build();
		}

		protected abstract boolean exitConditionTrue(View view);
	}

}

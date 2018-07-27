package org.briarproject.briar.android.test;

import android.app.Activity;
import android.support.test.espresso.PerformException;
import android.support.test.espresso.UiController;
import android.support.test.espresso.ViewAction;
import android.support.test.runner.lifecycle.ActivityLifecycleMonitor;
import android.support.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import android.view.View;

import org.hamcrest.Matcher;

import java.util.concurrent.TimeoutException;

import static android.support.test.espresso.matcher.ViewMatchers.isRoot;
import static android.support.test.espresso.util.HumanReadables.describe;
import static android.support.test.espresso.util.TreeIterables.breadthFirstViewTraversal;
import static android.support.test.runner.lifecycle.Stage.RESUMED;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ViewActions {

	private final static long TIMEOUT_MS = SECONDS.toMillis(10);
	private final static long WAIT_MS = 50;

	public static ViewAction waitUntilMatches(Matcher<View> viewMatcher) {
		return waitUntilMatches(viewMatcher, TIMEOUT_MS);
	}

	private static ViewAction waitUntilMatches(Matcher<View> viewMatcher,
			long timeout) {
		return new ViewAction() {
			@Override
			public Matcher<View> getConstraints() {
				return isRoot();
			}

			@Override
			public String getDescription() {
				return "Wait for view matcher " + viewMatcher +
						" to match within " + timeout + " milliseconds.";
			}

			@Override
			public void perform(final UiController uiController,
					final View view) {
				uiController.loopMainThreadUntilIdle();
				long endTime = currentTimeMillis() + timeout;

				do {
					for (View child : breadthFirstViewTraversal(view)) {
						if (viewMatcher.matches(child)) return;
					}
					uiController.loopMainThreadForAtLeast(WAIT_MS);
				}
				while (currentTimeMillis() < endTime);

				throw new PerformException.Builder()
						.withActionDescription(getDescription())
						.withViewDescription(describe(view))
						.withCause(new TimeoutException())
						.build();
			}
		};
	}

	public static ViewAction waitForActivityToResume(Activity activity) {
		return new ViewAction() {
			@Override
			public Matcher<View> getConstraints() {
				return isRoot();
			}

			@Override
			public String getDescription() {
				return "Wait for activity " + activity.getClass().getName() +
						" to resume within " + TIMEOUT_MS + " milliseconds.";
			}

			@Override
			public void perform(final UiController uiController,
					final View view) {
				uiController.loopMainThreadUntilIdle();
				long endTime = currentTimeMillis() + TIMEOUT_MS;
				ActivityLifecycleMonitor lifecycleMonitor =
						ActivityLifecycleMonitorRegistry.getInstance();
				do {
					if (lifecycleMonitor.getLifecycleStageOf(activity) ==
							RESUMED) return;
					uiController.loopMainThreadForAtLeast(WAIT_MS);
				}
				while (currentTimeMillis() < endTime);

				throw new PerformException.Builder()
						.withActionDescription(getDescription())
						.withViewDescription(describe(view))
						.withCause(new TimeoutException())
						.build();
			}
		};
	}

}

package org.briarproject.briar.android;

import android.app.Activity;
import android.app.Application.ActivityLifecycleCallbacks;
import android.os.Bundle;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
class BackgroundMonitor implements ActivityLifecycleCallbacks {

	private final AtomicInteger foregroundActivities = new AtomicInteger(0);

	boolean isRunningInBackground() {
		return foregroundActivities.get() == 0;
	}

	@Override
	public void onActivityCreated(Activity a, @Nullable Bundle state) {
	}

	@Override
	public void onActivityStarted(Activity a) {
		foregroundActivities.incrementAndGet();
	}

	@Override
	public void onActivityResumed(Activity a) {
	}

	@Override
	public void onActivityPaused(Activity a) {
	}

	@Override
	public void onActivityStopped(Activity a) {
		foregroundActivities.decrementAndGet();
	}

	@Override
	public void onActivitySaveInstanceState(Activity a,
			@Nullable Bundle outState) {
	}

	@Override
	public void onActivityDestroyed(Activity a) {
	}
}

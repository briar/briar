package org.briarproject.briar.api.android;

import android.support.annotation.UiThread;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Collection;

@NotNullByDefault
public interface ScreenFilterMonitor {

	/**
	 * Returns the details of all apps that have requested the
	 * SYSTEM_ALERT_WINDOW permission, excluding system apps, Google Play
	 * Services, and any apps that have been allowed by calling
	 * {@link #allowApps(Collection)}.
	 */
	@UiThread
	Collection<AppDetails> getApps();

	/**
	 * Allows the apps with the given package names to use overlay windows.
	 * They will not be returned by future calls to {@link #getApps()}.
	 */
	@UiThread
	void allowApps(Collection<String> packageNames);

	class AppDetails {

		public final String name;
		public final String packageName;

		public AppDetails(String name, String packageName) {
			this.name = name;
			this.packageName = packageName;
		}
	}
}

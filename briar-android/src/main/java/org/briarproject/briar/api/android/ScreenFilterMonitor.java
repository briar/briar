package org.briarproject.briar.api.android;

import android.support.annotation.UiThread;

import java.util.Collection;
import java.util.Set;

public interface ScreenFilterMonitor {

	@UiThread
	Set<String> getApps();

	@UiThread
	void storeAppsAsShown(Collection<String> s, boolean persistent);
}

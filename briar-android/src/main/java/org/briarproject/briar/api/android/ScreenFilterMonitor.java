package org.briarproject.briar.api.android;

import android.support.annotation.UiThread;

import java.util.Set;

public interface ScreenFilterMonitor {

	@UiThread
	Set<String> getApps();
}

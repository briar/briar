package org.briarproject.android.controller;

public interface ActivityLifecycleController {
	void onActivityCreate();

	void onActivityResume();

	void onActivityPause();

	void onActivityDestroy();
}

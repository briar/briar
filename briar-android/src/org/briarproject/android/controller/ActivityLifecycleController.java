package org.briarproject.android.controller;

import android.app.Activity;

public interface ActivityLifecycleController {

	void onActivityCreate(Activity activity);

	void onActivityResume();

	void onActivityPause();

	void onActivityDestroy();
}

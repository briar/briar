package org.briarproject.android;

import android.support.annotation.UiThread;

public interface DestroyableActivity {

	void runOnUiThread(Runnable runnable);

	@UiThread
	boolean hasBeenDestroyed();

}

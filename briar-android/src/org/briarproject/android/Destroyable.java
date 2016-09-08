package org.briarproject.android;

import android.support.annotation.UiThread;

public interface Destroyable {

	@UiThread
	boolean hasBeenDestroyed();

}

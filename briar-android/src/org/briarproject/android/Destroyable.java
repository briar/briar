package org.briarproject.android;

import android.support.annotation.UiThread;

interface Destroyable {

	@UiThread
	boolean hasBeenDestroyed();

}

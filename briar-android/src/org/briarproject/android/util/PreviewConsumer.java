package org.briarproject.android.util;

import android.hardware.Camera;
import android.support.annotation.UiThread;

@SuppressWarnings("deprecation")
public interface PreviewConsumer {

	@UiThread
	void start(Camera camera);

	@UiThread
	void stop();
}

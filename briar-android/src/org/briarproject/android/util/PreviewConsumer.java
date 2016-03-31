package org.briarproject.android.util;

import android.hardware.Camera;

@SuppressWarnings("deprecation")
public interface PreviewConsumer {

	void start(Camera camera);

	void stop();
}

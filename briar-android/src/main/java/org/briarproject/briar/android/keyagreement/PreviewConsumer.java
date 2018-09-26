package org.briarproject.briar.android.keyagreement;

import android.hardware.Camera;
import android.support.annotation.UiThread;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@SuppressWarnings("deprecation")
@NotNullByDefault
public interface PreviewConsumer {

	@UiThread
	void start(Camera camera, int cameraIndex);

	@UiThread
	void stop();
}

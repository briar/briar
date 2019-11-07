package org.briarproject.briar.android.keyagreement;

import android.hardware.Camera;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import androidx.annotation.UiThread;

@SuppressWarnings("deprecation")
@NotNullByDefault
interface PreviewConsumer {

	@UiThread
	void start(Camera camera, int cameraIndex);

	@UiThread
	void stop();
}

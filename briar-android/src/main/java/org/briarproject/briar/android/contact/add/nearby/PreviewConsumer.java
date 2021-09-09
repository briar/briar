package org.briarproject.briar.android.contact.add.nearby;

import android.hardware.Camera;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import androidx.annotation.UiThread;

@NotNullByDefault
interface PreviewConsumer {

	@UiThread
	void start(Camera camera, int cameraIndex);

	@UiThread
	void stop();
}

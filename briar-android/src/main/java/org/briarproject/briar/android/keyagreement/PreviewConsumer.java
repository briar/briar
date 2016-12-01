package org.briarproject.briar.android.keyagreement;

import android.hardware.Camera;
import android.support.annotation.UiThread;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@SuppressWarnings("deprecation")
@NotNullByDefault
interface PreviewConsumer {

	@UiThread
	void start(Camera camera);

	@UiThread
	void stop();
}

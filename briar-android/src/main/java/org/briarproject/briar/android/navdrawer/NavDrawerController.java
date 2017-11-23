package org.briarproject.briar.android.navdrawer;

import android.content.Context;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.briar.android.controller.ActivityLifecycleController;
import org.briarproject.briar.android.controller.handler.ResultHandler;

@NotNullByDefault
public interface NavDrawerController extends ActivityLifecycleController {

	enum ExpiryWarning { SHOW, NO, UPDATE }

	boolean isTransportRunning(TransportId transportId);

	void showExpiryWarning(ResultHandler<ExpiryWarning> handler);

	void expiryWarningDismissed();

	void shouldAskForDozeWhitelisting(Context ctx,
			ResultHandler<Boolean> handler);

}

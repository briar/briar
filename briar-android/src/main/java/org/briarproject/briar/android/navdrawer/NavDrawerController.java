package org.briarproject.briar.android.navdrawer;

import android.content.Context;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Plugin.State;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.briar.android.controller.ActivityLifecycleController;
import org.briarproject.briar.android.controller.handler.ResultHandler;

@NotNullByDefault
public interface NavDrawerController extends ActivityLifecycleController {

	State getTransportState(TransportId transportId);

	void showExpiryWarning(ResultHandler<Boolean> handler);

	void expiryWarningDismissed();

	void shouldAskForDozeWhitelisting(Context ctx,
			ResultHandler<Boolean> handler);

}

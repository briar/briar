package org.briarproject.briar.android.navdrawer;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.briar.android.controller.ActivityLifecycleController;
import org.briarproject.briar.android.controller.handler.ResultHandler;

@NotNullByDefault
public interface NavDrawerController extends ActivityLifecycleController {

	boolean isTransportRunning(TransportId transportId);

	void showExpiryWarning(final ResultHandler<Boolean> handler);

	void expiryWarningDismissed();

}

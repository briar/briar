package org.briarproject.briar.android.navdrawer;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.briar.android.controller.ActivityLifecycleController;

@NotNullByDefault
public interface NavDrawerController extends ActivityLifecycleController {

	boolean isTransportRunning(TransportId transportId);

}

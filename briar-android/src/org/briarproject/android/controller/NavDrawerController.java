package org.briarproject.android.controller;

import org.briarproject.api.TransportId;

public interface NavDrawerController extends ActivityLifecycleController {

	boolean isTransportRunning(TransportId transportId);

}

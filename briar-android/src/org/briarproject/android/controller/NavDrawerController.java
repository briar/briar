package org.briarproject.android.controller;

import org.briarproject.android.controller.handler.ResultHandler;
import org.briarproject.api.TransportId;
import org.briarproject.api.identity.LocalAuthor;

public interface NavDrawerController extends ActivityLifecycleController {

	void setTransportListener(TransportStateListener transportListener);

	boolean isTransportRunning(TransportId transportId);

	void storeLocalAuthor(LocalAuthor author,
			ResultHandler<Void> resultHandler);

	LocalAuthor removeAuthorHandle(long handle);
}

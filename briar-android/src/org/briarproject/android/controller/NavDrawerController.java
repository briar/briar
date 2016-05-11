package org.briarproject.android.controller;

import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.api.TransportId;
import org.briarproject.api.identity.LocalAuthor;

public interface NavDrawerController extends BriarController {

	void setTransportListener(TransportStateListener transportListener);

	boolean isTransportRunning(TransportId transportId);

	void storeLocalAuthor(LocalAuthor author,
			UiResultHandler<Void> resultHandler);

	LocalAuthor removeAuthorHandle(long handle);
}

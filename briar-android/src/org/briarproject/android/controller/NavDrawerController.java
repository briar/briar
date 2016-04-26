package org.briarproject.android.controller;

import org.briarproject.android.controller.handler.ResultExceptionHandler;
import org.briarproject.api.TransportId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.LocalAuthor;

public interface NavDrawerController extends BriarController {
	void setTransportListener(TransportStateListener transportListener);

	boolean transportRunning(TransportId transportId);

	void storeLocalAuthor(LocalAuthor author,
			ResultExceptionHandler<Void, DbException> resultHandler);

	LocalAuthor removeAuthorHandle(long handle);
}

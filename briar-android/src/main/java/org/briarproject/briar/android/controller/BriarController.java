package org.briarproject.briar.android.controller;

import org.briarproject.briar.android.controller.handler.ResultHandler;

public interface BriarController extends ActivityLifecycleController {

	void startAndBindService();

	boolean hasEncryptionKey();

	void signOut(ResultHandler<Void> eventHandler);
}

package org.briarproject.briar.android.controller;

import org.briarproject.briar.android.controller.handler.ResultHandler;

public interface BriarController extends ActivityLifecycleController {

	void startAndBindService();

	boolean hasEncryptionKey();

	/**
	 * Returns true via the handler when the app has dozed
	 * without being white-listed.
	 */
	void hasDozed(ResultHandler<Boolean> handler);

	void doNotAskAgainForDozeWhiteListing();

	void signOut(ResultHandler<Void> eventHandler);
}

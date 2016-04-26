package org.briarproject.android.controller;


import org.briarproject.android.controller.handler.ResultHandler;

public interface BriarController extends ActivityLifecycleController {
	void runOnDbThread(final Runnable task);

	void startAndBindService();

	boolean encryptionKey();

	void signOut(ResultHandler<Void> eventHandler);
}

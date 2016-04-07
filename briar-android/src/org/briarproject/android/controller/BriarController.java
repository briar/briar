package org.briarproject.android.controller;


public interface BriarController extends ActivityLifecycleController {
	void runOnDbThread(final Runnable task);

	void startAndBindService();

	void unbindService();

	boolean encryptionKey();

	void signOut(ResultHandler<Void, RuntimeException> eventHandler);
}

package org.briarproject.briar.android.controller;

import android.app.Activity;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.CallSuper;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.briar.android.BriarService;
import org.briarproject.briar.android.BriarService.BriarServiceConnection;
import org.briarproject.briar.android.controller.handler.ResultHandler;

import java.util.logging.Logger;

import javax.inject.Inject;

public class BriarControllerImpl implements BriarController {

	private static final Logger LOG =
			Logger.getLogger(BriarControllerImpl.class.getName());

	private final BriarServiceConnection serviceConnection;
	private final DatabaseConfig databaseConfig;
	private final Activity activity;

	private boolean bound = false;

	@Inject
	BriarControllerImpl(BriarServiceConnection serviceConnection,
			DatabaseConfig databaseConfig, Activity activity) {
		this.serviceConnection = serviceConnection;
		this.databaseConfig = databaseConfig;
		this.activity = activity;
	}

	@Override
	@CallSuper
	public void onActivityCreate(Activity activity) {
		if (databaseConfig.getEncryptionKey() != null) startAndBindService();
	}

	@Override
	public void onActivityStart() {
	}

	@Override
	public void onActivityStop() {
	}

	@Override
	@CallSuper
	public void onActivityDestroy() {
		unbindService();
	}

	@Override
	public void startAndBindService() {
		activity.startService(new Intent(activity, BriarService.class));
		bound = activity.bindService(new Intent(activity, BriarService.class),
				serviceConnection, 0);
	}

	@Override
	public boolean hasEncryptionKey() {
		return databaseConfig.getEncryptionKey() != null;
	}

	@Override
	public void signOut(final ResultHandler<Void> eventHandler) {
		new Thread() {
			@Override
			public void run() {
				try {
					// Wait for the service to finish starting up
					IBinder binder = serviceConnection.waitForBinder();
					BriarService service =
							((BriarService.BriarBinder) binder).getService();
					service.waitForStartup();
					// Shut down the service and wait for it to shut down
					LOG.info("Shutting down service");
					service.shutdown();
					service.waitForShutdown();
				} catch (InterruptedException e) {
					LOG.warning("Interrupted while waiting for service");
				}
				eventHandler.onResult(null);
			}
		}.start();
	}

	private void unbindService() {
		if (bound) activity.unbindService(serviceConnection);
	}

}

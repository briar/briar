package org.briarproject.android.controller;

import android.app.Activity;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.CallSuper;

import org.briarproject.android.BriarService;
import org.briarproject.android.BriarService.BriarServiceConnection;
import org.briarproject.android.controller.handler.ResultHandler;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.lifecycle.LifecycleManager;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

public class BriarControllerImpl implements BriarController {

	private static final Logger LOG =
			Logger.getLogger(BriarControllerImpl.class.getName());

	@Inject
	protected BriarServiceConnection serviceConnection;
	@Inject
	protected DatabaseConfig databaseConfig;
	// Fields that are accessed from background threads must be volatile
	@Inject
	@DatabaseExecutor
	protected volatile Executor dbExecutor;
	@Inject
	protected volatile LifecycleManager lifecycleManager;
	@Inject
	protected Activity activity;

	private boolean bound = false;

	@Inject
	public BriarControllerImpl() {

	}

	@Override
	@CallSuper
	public void onActivityCreate() {
		if (databaseConfig.getEncryptionKey() != null) startAndBindService();
	}

	@Override
	@CallSuper
	public void onActivityResume() {
	}

	@Override
	@CallSuper
	public void onActivityPause() {
	}

	@Override
	@CallSuper
	public void onActivityDestroy() {
		unbindService();
	}

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
					BriarService service = ((BriarService.BriarBinder) binder).getService();
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

	public void runOnDbThread(final Runnable task) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
					task.run();
				} catch (InterruptedException e) {
					LOG.warning("Interrupted while waiting for database");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

}

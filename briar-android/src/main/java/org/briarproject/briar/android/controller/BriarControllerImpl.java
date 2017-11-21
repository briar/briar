package org.briarproject.briar.android.controller;

import android.app.Activity;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.briar.android.BriarService;
import org.briarproject.briar.android.BriarService.BriarServiceConnection;
import org.briarproject.briar.android.controller.handler.ResultHandler;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.briar.android.settings.SettingsFragment.SETTINGS_NAMESPACE;
import static org.briarproject.briar.android.util.UiUtils.needsDozeWhitelisting;

public class BriarControllerImpl implements BriarController {

	private static final Logger LOG =
			Logger.getLogger(BriarControllerImpl.class.getName());

	public static final String DOZE_ASK_AGAIN = "dozeAskAgain";

	private final BriarServiceConnection serviceConnection;
	private final DatabaseConfig databaseConfig;
	@DatabaseExecutor
	private final Executor databaseExecutor;
	private final SettingsManager settingsManager;
	private final Activity activity;

	private boolean bound = false;

	@Nullable
	private volatile BriarService service;

	@Inject
	BriarControllerImpl(BriarServiceConnection serviceConnection,
			DatabaseConfig databaseConfig,
			@DatabaseExecutor Executor databaseExecutor,
			SettingsManager settingsManager, Activity activity) {
		this.serviceConnection = serviceConnection;
		this.databaseConfig = databaseConfig;
		this.databaseExecutor = databaseExecutor;
		this.settingsManager = settingsManager;
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
		if (!bound) throw new IllegalStateException();

		new Thread(() -> {
			try {
				service = getBriarService();
			} catch (InterruptedException e) {
				LOG.warning("Interrupted while waiting for service");
			}
		}).start();
	}

	@Override
	public boolean hasEncryptionKey() {
		return databaseConfig.getEncryptionKey() != null;
	}

	@Override
	public void hasDozed(ResultHandler<Boolean> handler) {
		BriarService briarService = service;
		if (briarService == null || !briarService.hasDozed() ||
				!needsDozeWhitelisting(activity)) {
			handler.onResult(false);
			return;
		}
		if (briarService.hasDozed()) briarService.resetDozeFlag();
		databaseExecutor.execute(() -> {
			try {
				Settings settings =
						settingsManager.getSettings(SETTINGS_NAMESPACE);
				boolean ask = settings.getBoolean(DOZE_ASK_AGAIN, true);
				handler.onResult(ask);
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING))
					LOG.log(WARNING, e.toString(), e);
			}
		});
	}

	@Override
	public void doNotAskAgainForDozeWhiteListing() {
		databaseExecutor.execute(() -> {
			try {
				Settings settings = new Settings();
				settings.putBoolean(DOZE_ASK_AGAIN, false);
				settingsManager.mergeSettings(settings, SETTINGS_NAMESPACE);
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING))
					LOG.log(WARNING, e.toString(), e);
			}
		});
	}

	@Override
	public void signOut(ResultHandler<Void> eventHandler) {
		new Thread() {
			@Override
			@SuppressWarnings("ConstantConditions")
			public void run() {
				try {
					if (service == null) service = getBriarService();
					// Wait for the service to finish starting up
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

	@WorkerThread
	private BriarService getBriarService() throws InterruptedException {
		IBinder binder = serviceConnection.waitForBinder();
		return ((BriarService.BriarBinder) binder).getService();
	}

	private void unbindService() {
		if (bound) activity.unbindService(serviceConnection);
	}

}

package org.briarproject.android;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import org.briarproject.android.BriarService.BriarBinder;
import org.briarproject.android.BriarService.BriarServiceConnection;
import org.briarproject.android.panic.ExitActivity;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.lifecycle.LifecycleManager;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;

@SuppressLint("Registered")
public abstract class BriarActivity extends BaseActivity {

	public static final String KEY_LOCAL_AUTHOR_HANDLE = "briar.LOCAL_AUTHOR_HANDLE";
	public static final String KEY_STARTUP_FAILED = "briar.STARTUP_FAILED";

	public static final int REQUEST_PASSWORD = 1;

	private static final Logger LOG =
			Logger.getLogger(BriarActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private DatabaseConfig databaseConfig;
	private boolean bound = false;

	// Fields that are accessed from background threads must be volatile
	@Inject @DatabaseExecutor private volatile Executor dbExecutor;
	@Inject private volatile LifecycleManager lifecycleManager;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		if (databaseConfig.getEncryptionKey() != null) startAndBindService();
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		if (request == REQUEST_PASSWORD) {
			if (result == RESULT_OK) startAndBindService();
			else finish();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (databaseConfig.getEncryptionKey() == null && !isFinishing()) {
			Intent i = new Intent(this, PasswordActivity.class);
			i.setFlags(FLAG_ACTIVITY_NO_ANIMATION | FLAG_ACTIVITY_SINGLE_TOP);
			startActivityForResult(i, REQUEST_PASSWORD);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService();
	}

	private void startAndBindService() {
		startService(new Intent(this, BriarService.class));
		bound = bindService(new Intent(this, BriarService.class),
				serviceConnection, 0);
	}

	private void unbindService() {
		if (bound) unbindService(serviceConnection);
	}

	protected void signOut(final boolean removeFromRecentApps) {
		new Thread() {
			@Override
			public void run() {
				try {
					// Wait for the service to finish starting up
					IBinder binder = serviceConnection.waitForBinder();
					BriarService service = ((BriarBinder) binder).getService();
					service.waitForStartup();
					// Shut down the service and wait for it to shut down
					LOG.info("Shutting down service");
					service.shutdown();
					service.waitForShutdown();
				} catch (InterruptedException e) {
					LOG.warning("Interrupted while waiting for service");
					Thread.currentThread().interrupt();
				}
				if (removeFromRecentApps) startExitActivity();
				else finishAndExit();
			}
		}.start();
	}

	protected void signOut() {
		signOut(false);
	}

	private void startExitActivity() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Intent intent = new Intent(BriarActivity.this,
						ExitActivity.class);
				intent.addFlags(FLAG_ACTIVITY_NEW_TASK
						| FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
						| FLAG_ACTIVITY_NO_ANIMATION);
				if (Build.VERSION.SDK_INT >= 11)
					intent.addFlags(FLAG_ACTIVITY_CLEAR_TASK);
				startActivity(intent);
			}
		});
	}

	private void finishAndExit() {
		runOnUiThread(new Runnable() {
			public void run() {
				if (Build.VERSION.SDK_INT >= 21) finishAndRemoveTask();
				else finish();
				LOG.info("Exiting");
				System.exit(0);
			}
		});
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

	protected void finishOnUiThread() {
		runOnUiThread(new Runnable() {
			public void run() {
				finish();
			}
		});
	}
}

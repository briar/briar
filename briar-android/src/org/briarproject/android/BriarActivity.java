package org.briarproject.android;

import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static java.util.logging.Level.INFO;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.android.BriarService.BriarBinder;
import org.briarproject.android.BriarService.BriarServiceConnection;
import org.briarproject.api.android.DatabaseUiExecutor;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.lifecycle.LifecycleManager;

import roboguice.activity.RoboFragmentActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

public class BriarActivity extends RoboFragmentActivity {

	public static final int REQUEST_PASSWORD = 1;

	private static final Logger LOG =
			Logger.getLogger(BriarActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private DatabaseConfig databaseConfig;
	private boolean bound = false;

	// Fields that are accessed from background threads must be volatile
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;
	@Inject private volatile LifecycleManager lifecycleManager;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		if(databaseConfig.getEncryptionKey() != null) startAndBindService();
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		if(request == REQUEST_PASSWORD) {
			if(result == RESULT_OK) startAndBindService();
			else finish();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if(databaseConfig.getEncryptionKey() == null && !isFinishing()) {
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
		startService(new Intent(BriarService.class.getName()));
		bound = bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	private void unbindService() {
		if(bound) unbindService(serviceConnection);
	}

	protected void signOut() {
		new Thread() {
			@Override
			public void run() {
				try {
					// Wait for the service to finish starting up
					IBinder binder = serviceConnection.waitForBinder();
					BriarService service = ((BriarBinder) binder).getService();
					service.waitForStartup();
					// Shut down the service and wait for it to shut down
					if(LOG.isLoggable(INFO)) LOG.info("Shutting down service");
					service.shutdown();
					service.waitForShutdown();
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for service");
				}
				// Finish the activity and kill the JVM
				runOnUiThread(new Runnable() {
					public void run() {
						finish();
						if(LOG.isLoggable(INFO)) LOG.info("Exiting");
						System.exit(0);
					}
				});
			}
		}.start();
	}

	protected void runOnDbThread(final Runnable task) {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
					task.run();
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for database");
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

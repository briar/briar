package org.briarproject.android;

import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;
import static java.util.logging.Level.INFO;

import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.android.BriarService.BriarBinder;
import org.briarproject.android.BriarService.BriarServiceConnection;
import org.briarproject.api.db.DatabaseConfig;

import roboguice.activity.RoboFragmentActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

public class BriarActivity extends RoboFragmentActivity {

	// This build expires on 7 February 2014
	private static final long EXPIRY_DATE = 1391731200 * 1000L;
	private static final int REQUEST_PASSWORD = 1;

	private static final Logger LOG =
			Logger.getLogger(BriarActivity.class.getName());

	private final BriarServiceConnection serviceConnection =
			new BriarServiceConnection();

	@Inject private DatabaseConfig databaseConfig;
	private boolean bound = false;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		if(LOG.isLoggable(INFO)) LOG.info("Created");
		if(System.currentTimeMillis() >= EXPIRY_DATE) {
			if(LOG.isLoggable(INFO)) LOG.info("Expired");
			Intent i = new Intent(this, ExpiredActivity.class);
			i.setFlags(FLAG_ACTIVITY_NO_ANIMATION);
			startActivity(i);
			finish();
		} else if(databaseConfig.getEncryptionKey() == null) {
			if(LOG.isLoggable(INFO)) LOG.info("No password");
			Intent i = new Intent(this, PasswordActivity.class);
			i.setFlags(FLAG_ACTIVITY_NO_ANIMATION);
			startActivityForResult(i, REQUEST_PASSWORD);
		} else {
			startAndBindService();
		}
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
	public void onDestroy() {
		super.onDestroy();
		unbindService();
	}

	protected void startAndBindService() {
		startService(new Intent(BriarService.class.getName()));
		bound = bindService(new Intent(BriarService.class.getName()),
				serviceConnection, 0);
	}

	protected void unbindService() {
		if(bound) unbindService(serviceConnection);
	}

	protected void quit() {
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
}

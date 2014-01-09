package org.briarproject.android;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static java.util.logging.Level.INFO;

import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.api.android.AndroidExecutor;
import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.lifecycle.LifecycleManager;

import roboguice.service.RoboService;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

public class BriarService extends RoboService {

	private static final Logger LOG =
			Logger.getLogger(BriarService.class.getName());

	private final Binder binder = new BriarBinder();

	@Inject private DatabaseConfig databaseConfig;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile LifecycleManager lifecycleManager;
	@Inject private volatile AndroidExecutor androidExecutor;
	private volatile boolean started = false;

	@Override
	public void onCreate() {
		super.onCreate();
		if(LOG.isLoggable(INFO)) LOG.info("Created");
		if(databaseConfig.getEncryptionKey() == null) {
			if(LOG.isLoggable(INFO)) LOG.info("No database key");
			stopSelf();
			return;
		}
		// Show an ongoing notification that the service is running
		NotificationCompat.Builder b = new NotificationCompat.Builder(this);
		b.setSmallIcon(R.drawable.notification_icon);
		b.setContentTitle(getText(R.string.notification_title));
		b.setContentText(getText(R.string.notification_text));
		b.setWhen(0); // Don't show the time
		b.setOngoing(true);
		// Touch the notification to show the home screen
		Intent i = new Intent(this, HomeScreenActivity.class);
		i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP |
				FLAG_ACTIVITY_SINGLE_TOP);
		b.setContentIntent(PendingIntent.getActivity(this, 0, i, 0));
		startForeground(1, b.build());
		// Start the services in a background thread
		new Thread() {
			@Override
			public void run() {
				if(lifecycleManager.startServices()) {
					started = true;
				} else {
					if(LOG.isLoggable(INFO)) LOG.info("Startup failed");
					Intent i = new Intent(BriarService.this,
							HomeScreenActivity.class);
					i.setFlags(FLAG_ACTIVITY_NEW_TASK |
							FLAG_ACTIVITY_CLEAR_TOP);
					i.putExtra("briar.STARTUP_FAILED", true);
					startActivity(i);
					stopSelf();
				}
			}
		}.start();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if(LOG.isLoggable(INFO)) LOG.info("Started");
		return START_NOT_STICKY; // Don't restart automatically if killed
	}

	public IBinder onBind(Intent intent) {
		if(LOG.isLoggable(INFO)) LOG.info("Bound");
		return binder;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if(LOG.isLoggable(INFO)) LOG.info("Destroyed");
		// Stop the services in a background thread
		new Thread() {
			@Override
			public void run() {
				androidExecutor.shutdown();
				if(started) lifecycleManager.stopServices();
			}
		}.start();
	}

	/** Waits for the database to be opened before returning. */
	public void waitForDatabase() throws InterruptedException {
		lifecycleManager.waitForDatabase();
	}

	/** Waits for all services to start before returning. */
	public void waitForStartup() throws InterruptedException {
		lifecycleManager.waitForStartup();
	}

	/** Waits for all services to stop before returning. */
	public void waitForShutdown() throws InterruptedException {
		lifecycleManager.waitForShutdown();
	}

	/** Starts the shutdown process. */
	public void shutdown() {
		stopSelf(); // This will call onDestroy()
	}

	public class BriarBinder extends Binder {

		/** Returns the bound service. */
		public BriarService getService() {
			return BriarService.this;
		}
	}

	public static class BriarServiceConnection implements ServiceConnection {

		private final CountDownLatch binderLatch = new CountDownLatch(1);

		private volatile IBinder binder = null;

		public void onServiceConnected(ComponentName name, IBinder binder) {
			this.binder = binder;
			binderLatch.countDown();
		}

		public void onServiceDisconnected(ComponentName name) {}

		/** Waits for the service to connect and returns its binder. */
		public IBinder waitForBinder() throws InterruptedException {
			binderLatch.await();
			return binder;
		}
	}
}

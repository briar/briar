package org.briarproject.briar.android;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.R;
import org.briarproject.briar.android.navdrawer.NavDrawerActivity;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.IMPORTANCE_NONE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.os.Build.VERSION.SDK_INT;
import static android.support.v4.app.NotificationCompat.CATEGORY_SERVICE;
import static android.support.v4.app.NotificationCompat.PRIORITY_MIN;
import static android.support.v4.app.NotificationCompat.VISIBILITY_SECRET;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult.ALREADY_RUNNING;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult.SUCCESS;

public class BriarService extends Service {

	public static String EXTRA_START_RESULT =
			"org.briarproject.briar.START_RESULT";
	public static String EXTRA_NOTIFICATION_ID =
			"org.briarproject.briar.FAILURE_NOTIFICATION_ID";
	public static String EXTRA_STARTUP_FAILED =
			"org.briarproject.briar.STARTUP_FAILED";

	private static final int ONGOING_NOTIFICATION_ID = 1;
	private static final int FAILURE_NOTIFICATION_ID = 2;

	// Channels are sorted by channel ID in the Settings app, so use IDs
	// that will sort below the main channels such as contacts
	private static final String ONGOING_CHANNEL_ID = "zForegroundService";
	private static final String FAILURE_CHANNEL_ID = "zStartupFailure";

	private static final Logger LOG =
			Logger.getLogger(BriarService.class.getName());

	private final AtomicBoolean created = new AtomicBoolean(false);
	private final Binder binder = new BriarBinder();

	@Inject
	protected DatabaseConfig databaseConfig;
	// Fields that are accessed from background threads must be volatile
	@Inject
	protected volatile LifecycleManager lifecycleManager;
	@Inject
	protected volatile AndroidExecutor androidExecutor;
	private volatile boolean started = false;

	@Override
	public void onCreate() {
		super.onCreate();

		BriarApplication application = (BriarApplication) getApplication();
		application.getApplicationComponent().inject(this);

		LOG.info("Created");
		if (created.getAndSet(true)) {
			LOG.info("Already created");
			stopSelf();
			return;
		}
		if (databaseConfig.getEncryptionKey() == null) {
			LOG.info("No database key");
			stopSelf();
			return;
		}
		// Create notification channels
		if (SDK_INT >= 26) {
			NotificationManager nm = (NotificationManager)
					getSystemService(NOTIFICATION_SERVICE);
			NotificationChannel ongoingChannel = new NotificationChannel(
					ONGOING_CHANNEL_ID,
					getString(R.string.ongoing_notification_title),
					IMPORTANCE_NONE);
			ongoingChannel.setLockscreenVisibility(VISIBILITY_SECRET);
			nm.createNotificationChannel(ongoingChannel);
			NotificationChannel failureChannel = new NotificationChannel(
					FAILURE_CHANNEL_ID,
					getString(R.string.startup_failed_notification_title),
					IMPORTANCE_DEFAULT);
			failureChannel.setLockscreenVisibility(VISIBILITY_SECRET);
			nm.createNotificationChannel(failureChannel);
		}
		// Show an ongoing notification that the service is running
		NotificationCompat.Builder b =
				new NotificationCompat.Builder(this, ONGOING_CHANNEL_ID);
		b.setSmallIcon(R.drawable.notification_ongoing);
		b.setColor(ContextCompat.getColor(this, R.color.briar_primary));
		b.setContentTitle(getText(R.string.ongoing_notification_title));
		b.setContentText(getText(R.string.ongoing_notification_text));
		b.setWhen(0); // Don't show the time
		b.setOngoing(true);
		Intent i = new Intent(this, NavDrawerActivity.class);
		i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
		b.setContentIntent(PendingIntent.getActivity(this, 0, i, 0));
		if (SDK_INT >= 21) {
			b.setCategory(CATEGORY_SERVICE);
			b.setVisibility(VISIBILITY_SECRET);
		}
		b.setPriority(PRIORITY_MIN);
		startForeground(ONGOING_NOTIFICATION_ID, b.build());
		// Start the services in a background thread
		new Thread(() -> {
			String nickname = databaseConfig.getLocalAuthorName();
			StartResult result = lifecycleManager.startServices(nickname);
			if (result == SUCCESS) {
				started = true;
			} else if (result == ALREADY_RUNNING) {
				LOG.info("Already running");
				stopSelf();
			} else {
				if (LOG.isLoggable(WARNING))
					LOG.warning("Startup failed: " + result);
				showStartupFailureNotification(result);
				stopSelf();
			}
		}).start();
	}

	private void showStartupFailureNotification(StartResult result) {
		androidExecutor.runOnUiThread(() -> {
			NotificationCompat.Builder b = new NotificationCompat.Builder(
					BriarService.this, FAILURE_CHANNEL_ID);
			b.setSmallIcon(android.R.drawable.stat_notify_error);
			b.setContentTitle(getText(
					R.string.startup_failed_notification_title));
			b.setContentText(getText(
					R.string.startup_failed_notification_text));
			Intent i = new Intent(BriarService.this,
					StartupFailureActivity.class);
			i.setFlags(FLAG_ACTIVITY_NEW_TASK);
			i.putExtra(EXTRA_START_RESULT, result);
			i.putExtra(EXTRA_NOTIFICATION_ID, FAILURE_NOTIFICATION_ID);
			b.setContentIntent(PendingIntent.getActivity(BriarService.this,
					0, i, FLAG_UPDATE_CURRENT));
			Object o = getSystemService(NOTIFICATION_SERVICE);
			NotificationManager nm = (NotificationManager) o;
			nm.notify(FAILURE_NOTIFICATION_ID, b.build());
			// Bring the dashboard to the front to clear the back stack
			i = new Intent(BriarService.this, NavDrawerActivity.class);
			i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
			i.putExtra(EXTRA_STARTUP_FAILED, true);
			startActivity(i);
		});
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_NOT_STICKY; // Don't restart automatically if killed
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		LOG.info("Destroyed");
		stopForeground(true);
		// Stop the services in a background thread
		new Thread(() -> {
			if (started) lifecycleManager.stopServices();
		}).start();
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		LOG.warning("Memory is low");
		// FIXME: Work out what to do about it
	}

	/**
	 * Waits for all services to start before returning.
	 */
	public void waitForStartup() throws InterruptedException {
		lifecycleManager.waitForStartup();
	}

	/**
	 * Waits for all services to stop before returning.
	 */
	public void waitForShutdown() throws InterruptedException {
		lifecycleManager.waitForShutdown();
	}

	/**
	 * Starts the shutdown process.
	 */
	public void shutdown() {
		stopSelf(); // This will call onDestroy()
	}

	public class BriarBinder extends Binder {

		/**
		 * Returns the bound service.
		 */
		public BriarService getService() {
			return BriarService.this;
		}
	}

	public static class BriarServiceConnection implements ServiceConnection {

		private final CountDownLatch binderLatch = new CountDownLatch(1);

		private volatile IBinder binder = null;

		@Override
		public void onServiceConnected(ComponentName name, IBinder binder) {
			this.binder = binder;
			binderLatch.countDown();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
		}

		/**
		 * Waits for the service to connect and returns its binder.
		 */
		public IBinder waitForBinder() throws InterruptedException {
			binderLatch.await();
			return binder;
		}
	}
}

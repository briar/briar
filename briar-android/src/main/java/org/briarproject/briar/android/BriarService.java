package org.briarproject.briar.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;

import com.bumptech.glide.Glide;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.AndroidWakeLockManager;
import org.briarproject.briar.R;
import org.briarproject.briar.android.logout.HideUiActivity;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.android.LockManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.core.app.NotificationCompat;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.IMPORTANCE_NONE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Intent.ACTION_SHUTDOWN;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;
import static android.os.Build.VERSION.SDK_INT;
import static androidx.core.app.NotificationCompat.VISIBILITY_SECRET;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult.ALREADY_RUNNING;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.StartResult.SUCCESS;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.briar.android.BriarApplication.ENTRY_ACTIVITY;
import static org.briarproject.briar.api.android.AndroidNotificationManager.FAILURE_CHANNEL_ID;
import static org.briarproject.briar.api.android.AndroidNotificationManager.FAILURE_NOTIFICATION_ID;
import static org.briarproject.briar.api.android.AndroidNotificationManager.ONGOING_CHANNEL_ID;
import static org.briarproject.briar.api.android.AndroidNotificationManager.ONGOING_NOTIFICATION_ID;
import static org.briarproject.briar.api.android.LockManager.ACTION_LOCK;

public class BriarService extends Service {

	public static String EXTRA_START_RESULT =
			"org.briarproject.briar.START_RESULT";
	public static String EXTRA_NOTIFICATION_ID =
			"org.briarproject.briar.FAILURE_NOTIFICATION_ID";
	public static String EXTRA_STARTUP_FAILED =
			"org.briarproject.briar.STARTUP_FAILED";

	private static final Logger LOG =
			Logger.getLogger(BriarService.class.getName());

	private final AtomicBoolean created = new AtomicBoolean(false);
	private final Binder binder = new BriarBinder();

	@Nullable
	private BroadcastReceiver receiver = null;
	private BriarApplication app;

	@Inject
	AndroidNotificationManager notificationManager;
	@Inject
	AccountManager accountManager;
	@Inject
	LockManager lockManager;
	@Inject
	AndroidWakeLockManager wakeLockManager;

	// Fields that are accessed from background threads must be volatile
	@Inject
	volatile LifecycleManager lifecycleManager;
	@Inject
	volatile AndroidExecutor androidExecutor;
	private volatile boolean started = false;

	@Override
	public void onCreate() {
		super.onCreate();

		app = (BriarApplication) getApplication();
		app.getApplicationComponent().inject(this);

		LOG.info("Created");
		if (created.getAndSet(true)) {
			LOG.info("Already created");
			stopSelf();
			return;
		}
		SecretKey dbKey = accountManager.getDatabaseKey();
		if (dbKey == null) {
			LOG.info("No database key");
			stopSelf();
			return;
		}

		// Hold a wake lock during startup
		wakeLockManager.runWakefully(() -> {
			// Create notification channels
			if (SDK_INT >= 26) {
				NotificationManager nm = (NotificationManager)
						requireNonNull(getSystemService(NOTIFICATION_SERVICE));
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
			Notification foregroundNotification =
					notificationManager.getForegroundNotification();
			startForeground(ONGOING_NOTIFICATION_ID, foregroundNotification);
			// Start the services in a background thread
			wakeLockManager.executeWakefully(() -> {
				StartResult result = lifecycleManager.startServices(dbKey);
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
			}, "LifecycleStartup");
			// Register for device shutdown broadcasts
			receiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					LOG.info("Device is shutting down");
					shutdownFromBackground();
				}
			};
			IntentFilter filter = new IntentFilter();
			filter.addAction(ACTION_SHUTDOWN);
			filter.addAction("android.intent.action.QUICKBOOT_POWEROFF");
			filter.addAction("com.htc.intent.action.QUICKBOOT_POWEROFF");
			registerReceiver(receiver, filter);
		}, "LifecycleStartup");
	}

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(Localizer.getInstance().setLocale(base));
		Localizer.getInstance().setLocale(this);
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
			NotificationManager nm = (NotificationManager)
					requireNonNull(getSystemService(NOTIFICATION_SERVICE));
			nm.notify(FAILURE_NOTIFICATION_ID, b.build());
			// Bring the dashboard to the front to clear the back stack
			i = new Intent(BriarService.this, ENTRY_ACTIVITY);
			i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
			i.putExtra(EXTRA_STARTUP_FAILED, true);
			startActivity(i);
		});
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (ACTION_LOCK.equals(intent.getAction())) {
			lockManager.setLocked(true);
		}
		return START_NOT_STICKY; // Don't restart automatically if killed
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public void onDestroy() {
		// Hold a wake lock during shutdown
		wakeLockManager.runWakefully(() -> {
			super.onDestroy();
			LOG.info("Destroyed");
			stopForeground(true);
			if (receiver != null) unregisterReceiver(receiver);
			// Stop the services in a background thread
			wakeLockManager.executeWakefully(() -> {
				if (started) lifecycleManager.stopServices();
			}, "LifecycleShutdown");
		}, "LifecycleShutdown");
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		LOG.warning("Memory is low");
		// If we're not in the foreground, clear the UI to save memory
		if (app.isRunningInBackground()) hideUi();
	}

	@Override
	public void onTrimMemory(int level) {
		super.onTrimMemory(level);
		if (level == TRIM_MEMORY_UI_HIDDEN) {
			LOG.info("Trim memory: UI hidden");
		} else if (level == TRIM_MEMORY_BACKGROUND) {
			LOG.info("Trim memory: added to LRU list");
		} else if (level == TRIM_MEMORY_MODERATE) {
			LOG.info("Trim memory: near middle of LRU list");
		} else if (level == TRIM_MEMORY_COMPLETE) {
			LOG.info("Trim memory: near end of LRU list");
		} else if (level == TRIM_MEMORY_RUNNING_MODERATE) {
			LOG.info("Trim memory: running moderately low");
			Glide.get(getApplicationContext()).clearMemory();
		} else if (level == TRIM_MEMORY_RUNNING_LOW) {
			LOG.info("Trim memory: running low");
			// TODO investigate if we can clear Glide cache here as well
		} else if (level == TRIM_MEMORY_RUNNING_CRITICAL) {
			LOG.warning("Trim memory: running critically low");
			// TODO investigate if we can clear Glide cache here as well
			// If we're not in the foreground, clear the UI to save memory
			if (app.isRunningInBackground()) hideUi();
		} else if (LOG.isLoggable(INFO)) {
			LOG.info("Trim memory: unknown level " + level);
		}
	}

	private void hideUi() {
		Intent i = new Intent(this, HideUiActivity.class);
		i.addFlags(FLAG_ACTIVITY_NEW_TASK
				| FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
				| FLAG_ACTIVITY_NO_ANIMATION
				| FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(i);
	}

	private void shutdownFromBackground() {
		// Hold a wake lock during shutdown
		wakeLockManager.runWakefully(() -> {
			// Stop the service
			stopSelf();
			// Hide the UI
			hideUi();
			// Wait for shutdown to complete, then exit
			wakeLockManager.executeWakefully(() -> {
				try {
					if (started) lifecycleManager.waitForShutdown();
				} catch (InterruptedException e) {
					LOG.info("Interrupted while waiting for shutdown");
				}
				LOG.info("Exiting");
				System.exit(0);
			}, "BackgroundShutdown");
		}, "BackgroundShutdown");
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
